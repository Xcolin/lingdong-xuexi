import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.SecureRandom;
import org.flywaydb.core.Flyway;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Explicit, opt-in demo migration. Never loaded by the application. */
class BootstrapDemo {
    static final List<String> USERS = List.of("admin", "auditor", "orgadmin", "teacher01", "teacher02", "parent01", "parent02");
    static Map<?,?> map(Object v) { return v instanceof Map<?,?> m ? m : Map.of(); }
    static String resolve(Object v) {
        String s = Objects.toString(v, "");
        if (s.startsWith("${") && s.endsWith("}")) {
            String[] p=s.substring(2,s.length()-1).split(":",2);
            return System.getenv().getOrDefault(p[0],p.length>1?p[1]:"");
        }
        return s;
    }
    static long count(Connection c, String sql) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=4) throw new IllegalArgumentException("Usage: config configured|ld_verify_YYYYMMDD_hex credentials.properties backupDir");
        Path credentials=Path.of(args[2]), backup=Path.of(args[3]);
        boolean configured=args[1].equals("configured");
        if(!configured&&!args[1].matches("ld_verify_[0-9]{8}_[a-f0-9]{8}")) throw new IllegalArgumentException("Unsafe schema");
        if(!Files.exists(backup.resolve("manifest.properties"))||!Files.exists(backup.resolve("restore-verified.properties"))) throw new IllegalStateException("Verified backup required");
        if(configured&&!Files.exists(backup.resolve("bootstrap-rehearsal.properties"))) throw new IllegalStateException("Demo rehearsal required");
        var config=map(new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(Path.of(args[0]))));
        var ds=map(map(config.get("spring")).get("datasource"));
        String url=resolve(ds.get("url")),user=resolve(ds.get("username")),password=resolve(ds.get("password"));
        if(!configured){int start=url.indexOf('/',"jdbc:mysql://".length());int q=url.indexOf('?',start);url=url.substring(0,start+1)+args[1]+(q<0?"":url.substring(q));}
        var main=Flyway.configure().dataSource(url,user,password).locations("filesystem:lingdong-bansui-server/src/main/resources/db/migration").cleanDisabled(true).load();
        if(!main.validateWithResult().validationSuccessful||main.info().pending().length!=0)throw new IllegalStateException("Main schema is not current");
        try(var c=DriverManager.getConnection(url,user,password)){
            if(count(c,"SELECT COUNT(*) FROM sys_role WHERE status='ENABLED' AND role_code IN ('SYS_ADMIN','SYS_AUDITOR','ORG_ADMIN','TEACHER','PARENT','STUDENT')")!=6)throw new IllegalStateException("Missing built-in roles");
            if(count(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_demo_history'")==0 && count(c,"SELECT COUNT(*) FROM sys_user WHERE username IN ('admin','auditor','orgadmin','teacher01','teacher02','parent01','parent02')")!=0)throw new IllegalStateException("Existing accounts must not be overwritten");
        }
        var props=new Properties();
        if(Files.exists(credentials)){try(var in=Files.newInputStream(credentials)){props.load(in);}}
        else {
            if(configured)throw new IllegalStateException("Reuse rehearsed credentials");
            var random=new SecureRandom();var encoder=new BCryptPasswordEncoder();
            for(String name:USERS){byte[] b=new byte[8];random.nextBytes(b);String p="Bn8"+HexFormat.of().formatHex(b);props.setProperty(name,p);props.setProperty(name+"Hash",encoder.encode(p));}
            Files.createDirectories(credentials.toAbsolutePath().getParent());
            try(var out=Files.newOutputStream(credentials,StandardOpenOption.CREATE_NEW)){props.store(out,"Local demo credentials. Do not commit or publish.");}
        }
        var placeholders=new HashMap<String,String>();
        for(String name:USERS){String hash=props.getProperty(name+"Hash");if(hash==null)throw new IllegalStateException("Missing credential hash");placeholders.put(name+"Hash",hash);}
        var demo=Flyway.configure().dataSource(url,user,password).table("flyway_demo_history")
            .locations("filesystem:tools/bootstrap-data/migrations").baselineOnMigrate(true).baselineVersion("0")
            .placeholders(placeholders).cleanDisabled(true).load();
        var result=demo.migrate();
        if(!demo.validateWithResult().validationSuccessful)throw new IllegalStateException("Demo migration validation failed");
        try(var c=DriverManager.getConnection(url,user,password)){
            for(String table:List.of("sys_user","sys_organization","sys_role","sys_permission","sys_role_permission","sys_user_role","edu_student","edu_parent_student","edu_teacher_class","growth_point_account"))System.out.println(table+".count="+count(c,"SELECT COUNT(*) FROM "+table));
            if(count(c,"SELECT COUNT(*) FROM sys_user WHERE id BETWEEN 2190000000000010001 AND 2190000000000010007")!=7)throw new IllegalStateException("Seed accounts incomplete");
            if(count(c,"SELECT COUNT(*) FROM edu_student WHERE id BETWEEN 2190000000000030001 AND 2190000000000030002")!=2)throw new IllegalStateException("Seed students incomplete");
        }
        System.out.println("demo.migrationsExecuted="+result.migrationsExecuted);
        if(!configured)Files.writeString(backup.resolve("bootstrap-rehearsal.properties"),"schema="+args[1]+"\nvalidation=true\n");
    }
}
