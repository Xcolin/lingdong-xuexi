import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 通用 MySQL 备份/恢复维护工具（任务 6.3 正式交付物，源自 2026-09-22 恢复演练验证版本）。
 * 用法：java MysqlRecovery <config.yml> <backup|restore|verify-source|verify-retained> <directory> [schema]
 * 凭据只在内存读取，备份整体 AES-256-GCM 加密（密钥经 stdin 传入 32 字节 Base64），不记录数据正文。
 * restore 仅允许 ld_verify_<8位日期>_<8位十六进制> 命名的隔离库，禁止误写业务库。
 * verify-retained 保留 2026-09-22 演练的两处放宽特例（失败 V56 历史被成功记录替换、V60 规定的六个权限字段变更），
 * 其余任何原始行字段变化都会判为失败。
 */
class MysqlRecovery {
    record Table(String name,String ddl,List<String> columns,List<Boolean> binary,List<Object[]> rows,String digest) implements Serializable {}
    record Snapshot(String fingerprint,String charset,String collation,List<Table> tables) implements Serializable {}
    static String stage="init";
    static String url,user,password;
    static Path directory;
    static Map<?,?> map(Object v) {return v instanceof Map<?,?> m ? m : Map.of();}
    static String str(Object v){return v==null?"":v.toString();}
    static String qi(String s){return "`"+s.replace("`","``")+"`";}
    static String resolve(Object v) {
        String s=str(v);
        if(s.startsWith("${") && s.endsWith("}")){
            String[] parts=s.substring(2,s.length()-1).split(":",2);
            return System.getenv().getOrDefault(parts[0],parts.length>1?parts[1]:"");
        }
        return s;
    }
    static Connection connect() throws Exception {
        var p=new Properties();
        p.setProperty("user",user);p.setProperty("password",password);
        p.setProperty("connectTimeout","10000");p.setProperty("socketTimeout","30000");
        p.setProperty("connectionTimeZone","UTC");
        var c=DriverManager.getConnection(url,p);
        try(var s=c.createStatement()){s.execute("SET SESSION time_zone='+00:00'");}
        return c;
    }
    static long number(Connection c,String sql) throws Exception{
        try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}
    }
    static String digest(Table t) throws Exception {
        var md=MessageDigest.getInstance("SHA-256");
        try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),md))){
            for(Object[] row:t.rows())for(Object v:row){
                if(v==null){out.writeInt(-1);continue;}
                byte[] bytes=v instanceof byte[] b ? b : v.toString().getBytes(StandardCharsets.UTF_8);
                out.writeInt(bytes.length);out.write(bytes);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
    static Table readTable(Connection c,String name) throws Exception{
        String ddl;
        try(var s=c.createStatement();var r=s.executeQuery("SHOW CREATE TABLE "+qi(name))){r.next();ddl=r.getString(2);}
        var columns=new ArrayList<String>();var binary=new ArrayList<Boolean>();var pk=new ArrayList<String>();
        try(var r=c.getMetaData().getPrimaryKeys(c.getCatalog(),null,name)){
            var keys=new TreeMap<Integer,String>();while(r.next())keys.put(r.getInt("KEY_SEQ"),r.getString("COLUMN_NAME"));pk.addAll(keys.values());
        }
        if(pk.isEmpty())throw new IllegalStateException("NoPrimaryKey");
        try(var s=c.prepareStatement("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND EXTRA NOT LIKE '%VIRTUAL GENERATED%' AND EXTRA NOT LIKE '%STORED GENERATED%' ORDER BY ordinal_position")){
            s.setString(1,name);try(var r=s.executeQuery()){while(r.next())columns.add(r.getString(1));}
        }
        String selection=String.join(",",columns.stream().map(MysqlRecovery::qi).toList());
        String ordering=String.join(",",pk.stream().map(MysqlRecovery::qi).toList());
        var rows=new ArrayList<Object[]>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT "+selection+" FROM "+qi(name)+" ORDER BY "+ordering)){
            var meta=r.getMetaData();
            for(int i=1;i<=columns.size();i++)binary.add(Set.of(Types.BINARY,Types.VARBINARY,Types.LONGVARBINARY,Types.BLOB).contains(meta.getColumnType(i)));
            while(r.next()){
                var row=new Object[columns.size()];
                for(int i=0;i<row.length;i++)row[i]=binary.get(i)?r.getBytes(i+1):r.getString(i+1);
                rows.add(row);
            }
        }
        var table=new Table(name,ddl,columns,binary,rows,"");
        return new Table(name,ddl,columns,binary,rows,digest(table));
    }
    static String fingerprint(Connection c) throws Exception{
        String source=url.split("\\?")[0]+"|"+c.getCatalog();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    }
    static byte[] key() throws Exception{
        String encoded=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8)).readLine();
        byte[] bytes=Base64.getDecoder().decode(encoded);
        if(bytes.length!=32)throw new IllegalArgumentException("InvalidBackupKey");
        return bytes;
    }
    static void backup() throws Exception{
        byte[] key=key();Snapshot snapshot;
        try(var c=connect()){
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            stage="backup_preflight";
            for(String object:List.of("triggers","routines","events")){
                String column=object.equals("triggers")?"TRIGGER_SCHEMA":object.equals("routines")?"ROUTINE_SCHEMA":"EVENT_SCHEMA";
                if(number(c,"SELECT COUNT(*) FROM information_schema."+object+" WHERE "+column+"=DATABASE()")!=0)throw new IllegalStateException("UnsupportedDatabaseObject");
            }
            if(number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND (table_type<>'BASE TABLE' OR engine<>'InnoDB')")!=0)throw new IllegalStateException("UnsupportedTable");
            String charset,collation;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT default_character_set_name,default_collation_name FROM information_schema.schemata WHERE schema_name=DATABASE()")){
                r.next();charset=r.getString(1);collation=r.getString(2);
            }
            var names=new ArrayList<String>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name")){while(r.next())names.add(r.getString(1));}
            var tables=new ArrayList<Table>();
            for(String name:names){stage="backup_"+name;tables.add(readTable(c,name));}
            snapshot=new Snapshot(fingerprint(c),charset,collation,tables);c.commit();
        }
        stage="encrypt_backup";
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        Path file=directory.resolve("database.enc");
        try(var fileOut=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){
            fileOut.write(nonce);
            try(var out=new ObjectOutputStream(new CipherOutputStream(fileOut,cipher))){out.writeObject(snapshot);}
        } finally {Arrays.fill(key,(byte)0);}
        var properties=new Properties();properties.setProperty("tables",str(snapshot.tables().size()));
        properties.setProperty("rows",str(snapshot.tables().stream().mapToLong(t->t.rows().size()).sum()));
        properties.setProperty("targetFingerprint",snapshot.fingerprint());
        properties.setProperty("backupSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        try(var out=Files.newOutputStream(directory.resolve("manifest.properties"))){properties.store(out,"Encrypted MySQL backup");}
        System.out.println("backup.tables="+properties.getProperty("tables"));
        System.out.println("backup.rows="+properties.getProperty("rows"));
        System.out.println("backup.encrypted=true");
    }
    static Snapshot load() throws Exception{
        byte[] key=key();byte[] data=Files.readAllBytes(directory.resolve("database.enc"));
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(data,12)));
        byte[] plain=cipher.doFinal(data,12,data.length-12);Arrays.fill(key,(byte)0);
        try(var in=new ObjectInputStream(new ByteArrayInputStream(plain))){return (Snapshot)in.readObject();}
        finally{Arrays.fill(plain,(byte)0);}
    }
    static void verifyRetained() throws Exception{
        Snapshot snap=load();long checked=0;
        var permissions=Set.of("LEARNING_TASK_CREATE","LEARNING_TASK_READ_MANAGED","LEARNING_TASK_PUBLISH","TASK_ASSIGNMENT_REVIEW","TASK_ASSIGNMENT_EXEMPT","TASK_ASSIGNMENT_DEFER");
        try(var c=connect()){
            c.setReadOnly(true);
            if(!snap.fingerprint().equals(fingerprint(c)))throw new IllegalStateException("TargetChanged");
            for(var t:snap.tables()){
                stage="verify_retained_"+t.name();var actual=readTable(c,t.name());
                String pk=t.name().equals("flyway_schema_history")?"installed_rank":"id";
                int oldPk=t.columns().indexOf(pk),newPk=actual.columns().indexOf(pk);
                if(oldPk<0||newPk<0)throw new IllegalStateException("UnsupportedPrimaryKey");
                var byId=new HashMap<Object,Object[]>();for(var row:actual.rows())byId.put(row[newPk],row);
                for(var row:t.rows()){
                    if(t.name().equals("flyway_schema_history")&&"56".equals(row[t.columns().indexOf("version")]))continue;
                    var current=byId.get(row[oldPk]);if(current==null)throw new IllegalStateException("OriginalRowMissing");
                    for(int col=0;col<t.columns().size();col++){
                        String name=t.columns().get(col);int newCol=actual.columns().indexOf(name);
                        if(newCol<0)throw new IllegalStateException("OriginalColumnMissing");
                        if(t.name().equals("sys_permission")&&permissions.contains(row[t.columns().indexOf("permission_code")])&&(name.equals("updated_at")||name.equals("client_type"))){
                            if(name.equals("client_type")&&!"BOTH".equals(current[newCol]))throw new IllegalStateException("UnexpectedPermissionChange");
                            continue;
                        }
                        Object a=row[col],b=current[newCol];
                        if(a instanceof byte[] aa&&b instanceof byte[] bb?!Arrays.equals(aa,bb):!Objects.equals(a,b))throw new IllegalStateException("OriginalValueChanged");
                    }
                    checked++;
                }
            }
            System.out.println("retained.originalRowsChecked="+checked);
            System.out.println("retained.allowedChanges=failedV56HistoryAndV60PermissionFields");
            System.out.println("retained.success=true");
            Files.writeString(directory.resolve("retained-verified.properties"),"rows="+checked+"\nverifiedAt="+java.time.Instant.now()+"\n");
        }
    }
    static void verifySource() throws Exception{
        Snapshot snap=load();
        try(var c=connect()){
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            if(!snap.fingerprint().equals(fingerprint(c)))throw new IllegalStateException("TargetChanged");
            if(number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")!=snap.tables().size())throw new IllegalStateException("SourceTableCountChanged");
            for(var t:snap.tables()){
                stage="verify_source_"+t.name();var actual=readTable(c,t.name());
                if(!actual.ddl().equals(t.ddl())||!actual.columns().equals(t.columns())||!actual.digest().equals(t.digest()))throw new IllegalStateException("SourceChanged");
            }
            c.commit();
            Files.writeString(directory.resolve("source-unchanged.properties"),"verifiedAt="+java.time.Instant.now()+"\ndataMatched=true\n");
            System.out.println("source.dataAndSchemaMatched=true");
        }
    }
    static void restore(String schema) throws Exception{
        if(!schema.matches("ld_verify_[0-9]{8}_[a-f0-9]{8}"))throw new IllegalArgumentException("UnsafeCloneName");
        Snapshot snap=load();
        try(var c=connect()){
            if(!snap.fingerprint().equals(fingerprint(c)))throw new IllegalStateException("TargetChanged");
            stage="create_isolated_clone";
            try(var s=c.createStatement()){
                s.execute("CREATE DATABASE "+qi(schema)+" CHARACTER SET "+qi(snap.charset())+" COLLATE "+qi(snap.collation()));
            }
            c.setCatalog(schema);
            try(var check=c.createStatement();var result=check.executeQuery("SELECT DATABASE()")){
                result.next();if(!schema.equals(result.getString(1)))throw new IllegalStateException("CloneDatabaseMismatch");
            }
            try(var s=c.createStatement()){
                s.execute("SET FOREIGN_KEY_CHECKS=0");
                for(var t:snap.tables()){stage="restore_schema_"+t.name();s.execute(t.ddl());}
            }
            c.setAutoCommit(false);
            for(var t:snap.tables()){
                stage="restore_rows_"+t.name();
                String columns=String.join(",",t.columns().stream().map(MysqlRecovery::qi).toList());
                String values=String.join(",",Collections.nCopies(t.columns().size(),"?"));
                try(var p=c.prepareStatement("INSERT INTO "+qi(t.name())+" ("+columns+") VALUES ("+values+")")){
                    for(var row:t.rows()){
                        for(int i=0;i<row.length;i++){
                            Object v=row[i];
                            if(v==null)p.setNull(i+1,Types.NULL);else if(v instanceof byte[] bytes)p.setBytes(i+1,bytes);else p.setString(i+1,v.toString());
                        }
                        p.executeUpdate();
                    }
                }
            }
            c.commit();c.setAutoCommit(true);
            try(var s=c.createStatement()){s.execute("SET FOREIGN_KEY_CHECKS=1");}
            for(var t:snap.tables()){
                stage="verify_restore_"+t.name();var actual=readTable(c,t.name());
                if(actual.rows().size()!=t.rows().size()||!actual.digest().equals(t.digest())){
                    System.out.println("restore.expectedRows="+t.rows().size()+" actualRows="+actual.rows().size());
                    System.out.println("restore.expectedColumns="+String.join(",",t.columns()));
                    System.out.println("restore.actualColumns="+String.join(",",actual.columns()));
                    if(actual.columns().equals(t.columns()) && actual.rows().size()==t.rows().size()){
                        for(int col=0;col<t.columns().size();col++){
                            int differences=0;
                            for(int row=0;row<t.rows().size();row++){
                                Object a=t.rows().get(row)[col],b=actual.rows().get(row)[col];
                                if(a instanceof byte[] aa && b instanceof byte[] bb ? !Arrays.equals(aa,bb) : !Objects.equals(a,b))differences++;
                            }
                            if(differences>0)System.out.println("restore.columnMismatch="+t.columns().get(col)+" rows="+differences);
                        }
                    }
                    throw new IllegalStateException("RestoreMismatch");
                }
            }
            Files.writeString(directory.resolve("restore-verified.properties"),"schema="+schema+"\ntables="+snap.tables().size()+"\ndataMatched=true\n",StandardOpenOption.CREATE_NEW);
            System.out.println("restore.schema="+schema);System.out.println("restore.dataMatched=true");
            System.out.println("restore.tables="+snap.tables().size());
        }
    }
    public static void main(String[] args){
        try{
            var config=map(new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(Path.of(args[0]))));
            var ds=map(map(config.get("spring")).get("datasource"));
            url=resolve(ds.get("url"));user=resolve(ds.get("username"));password=resolve(ds.get("password"));
            directory=Path.of(args[2]);Files.createDirectories(directory);
            if(args[1].equals("backup"))backup();else if(args[1].equals("restore"))restore(args[3]);else if(args[1].equals("verify-source"))verifySource();else if(args[1].equals("verify-retained"))verifyRetained();else throw new IllegalArgumentException("UnknownMode");
        }catch(Exception e){
            System.out.println("failure.stage="+stage);System.out.println("failure.type="+e.getClass().getSimpleName());
            if(e instanceof SQLException sql){System.out.println("failure.sqlState="+sql.getSQLState());System.out.println("failure.sqlCode="+sql.getErrorCode());}
            System.exit(2);
        }
    }
}
