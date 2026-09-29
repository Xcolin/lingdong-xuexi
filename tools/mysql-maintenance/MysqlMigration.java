import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Flyway 迁移校验/受控迁移工具（任务 6.3 正式交付物，源自 2026-09-22 恢复演练验证版本）。
 * 用法：java MysqlMigration <config.yml> <configured|ld_verify_...> <validate|migrate|recover> <backupDir>
 * validate：只读校验迁移历史与目标一致，无版本硬编码，适用于任意版本。
 * migrate/recover：面向 2026-09-22 V56 失败恢复演练的受控路径（保留版本断言 56/66-77/77 作为防误用护栏），
 * 含恢复前必须存在的备份/演练证据校验；重放需按当时记录核对版本范围，不得直接套用到其他失败场景。
 * 原则：Flyway 失败采用修复迁移（repair + 修正后续迁移脚本），不回滚、不改写已成功执行的历史脚本。
 */
class MysqlMigration {
    static String stage="init";
    static Map<?,?> map(Object v){return v instanceof Map<?,?> m?m:Map.of();}
    static String value(Object v){return v==null?"":v.toString();}
    static String resolve(Object v){
        String s=value(v);
        if(s.startsWith("${")&&s.endsWith("}")){String[] p=s.substring(2,s.length()-1).split(":",2);return System.getenv().getOrDefault(p[0],p.length>1?p[1]:"");}
        return s;
    }
    static long count(Connection c,String sql)throws Exception{
        try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}
    }
    static String qi(String name){return "`"+name.replace("`","``")+"`";}
    public static void main(String[] args){
        try{
            java.util.logging.LogManager.getLogManager().reset();
            var config=map(new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(Path.of(args[0]))));
            var ds=map(map(config.get("spring")).get("datasource"));
            String original=resolve(ds.get("url")),user=resolve(ds.get("username")),password=resolve(ds.get("password"));
            String schema=args[1], mode=args[2];Path backup=Path.of(args[3]);
            boolean configured=schema.equals("configured");
            if(!configured&&!schema.matches("ld_verify_[0-9]{8}_[a-f0-9]{8}"))throw new IllegalArgumentException("UnsafeSchema");
            String url=original;
            if(!configured){
                int start=original.indexOf('/',"jdbc:mysql://".length());int query=original.indexOf('?',start);
                url=original.substring(0,start+1)+schema+(query<0?"":original.substring(query));
            }
            var properties=new Properties();properties.setProperty("user",user);properties.setProperty("password",password);
            properties.setProperty("connectTimeout","10000");properties.setProperty("socketTimeout","30000");
            var flyway=Flyway.configure().dataSource(url,user,password)
                .locations("filesystem:lingdong-xuexi-server/src/main/resources/db/migration")
                .loggers("org.flywaydb.core.internal.logging.javautil.JavaUtilLogCreator").cleanDisabled(true).load();
            if(mode.equals("validate")){
                stage="validate";
                var v=flyway.validateWithResult();
                System.out.println("validation.success="+v.validationSuccessful);
                for(var error:v.invalidMigrations)System.out.println("validation.version="+error.version+" code="+error.errorDetails.errorCode);
                try(var c=DriverManager.getConnection(url,properties)){
                    try(var r=c.createStatement().executeQuery("SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")){
                        if(r.next())System.out.println("validation.latestVersion="+r.getString(1)+" latestSuccess="+r.getBoolean(2));
                    }
                    System.out.println("validation.failedHistoryRows="+count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"));
                }
                return;
            }
            if(!Files.exists(backup.resolve("restore-verified.properties")))throw new IllegalStateException("RestoreEvidenceMissing");
            if(configured&&!Files.exists(backup.resolve("clone-migration-verified.properties")))throw new IllegalStateException("RehearsalMissing");
            if(configured&&mode.equals("recover")){
                var checked=new Properties();
                try(var in=Files.newInputStream(backup.resolve("source-unchanged.properties"))){checked.load(in);}
                var age=java.time.Duration.between(java.time.Instant.parse(checked.getProperty("verifiedAt")),java.time.Instant.now());
                if(!"true".equals(checked.getProperty("dataMatched"))||age.isNegative()||age.toMinutes()>2)throw new IllegalStateException("FreshSourceVerificationMissing");
            }
            if(mode.equals("recover")){
                stage="verify_failed_migration";
                var validation=flyway.validateWithResult();
                long failed56=validation.invalidMigrations.stream().filter(e->"56".equals(e.version)&&"FAILED_VERSIONED_MIGRATION".equals(e.errorDetails.errorCode.toString())).count();
                boolean unexpected=validation.invalidMigrations.stream().anyMatch(e->{
                    String code=e.errorDetails.errorCode.toString();
                    if("56".equals(e.version)&&"FAILED_VERSIONED_MIGRATION".equals(code))return false;
                    if("RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(code)){
                        try{int n=Integer.parseInt(e.version);return n<57||n>77;}catch(Exception ignored){return true;}
                    }
                    return true;
                });
                if(failed56!=1||unexpected)
                    throw new IllegalStateException("UnexpectedMigrationState");
                try(var c=DriverManager.getConnection(url,properties)){
                    if(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE success=0")!=1
                        ||count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='56' AND success=0")!=1)
                        throw new IllegalStateException("UnexpectedFailedVersion");
                    if(count(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_import_job_row_result'")!=0)
                        throw new IllegalStateException("UnexpectedPartialTable");
                    if(count(c,"SELECT COUNT(*) FROM sys_import_job")!=0||count(c,"SELECT COUNT(*) FROM sys_import_export_template_field")!=0)
                        throw new IllegalStateException("PartialTableContainsData");
                    if(count(c,"SELECT COUNT(*) FROM sys_feature_toggle WHERE feature_code='DATA_IMPORT_VALIDATION'")!=0)
                        throw new IllegalStateException("UnexpectedSeeds");
                    stage="remove_only_empty_failed_tables";
                    try(var s=c.createStatement()){s.execute("DROP TABLE sys_import_job");s.execute("DROP TABLE sys_import_export_template_field");}
                }
                stage="repair_failed_history";flyway.repair();System.out.println("recovery.failedV56Removed=true");
            }else if(!mode.equals("migrate"))throw new IllegalArgumentException("UnknownMode");
            else if(configured){
                // 受控发布前置校验：无失败历史；未应用项全部为正常待发布迁移（不限版本范围）；必须存在演练证据。
                var v=flyway.validateWithResult();
                for(var e:v.invalidMigrations){
                    if(!"RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(e.errorDetails.errorCode.toString()))throw new IllegalStateException("UnsafeResumeState");
                }
                try(var c=DriverManager.getConnection(url,properties)){
                    if(count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE success=0")!=0)throw new IllegalStateException("FailedHistoryPresent");
                }
                if(!Files.exists(backup.resolve("retained-verified.properties"))&&!Files.exists(backup.resolve("source-unchanged.properties")))throw new IllegalStateException("RetainedEvidenceMissing");
            }
            stage="migrate";var result=flyway.migrate();System.out.println("migrate.count="+result.migrationsExecuted);
            var validation=flyway.validateWithResult();if(!validation.validationSuccessful)throw new IllegalStateException("PostValidationFailed");
            try(var c=DriverManager.getConnection(url,properties)){
                long failed=count(c,"SELECT COUNT(*) FROM flyway_schema_history WHERE success=0");
                long tables=count(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'");
                String latest;
                try(var r=c.createStatement().executeQuery("SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")){
                    r.next();latest=r.getString(1);
                }
                if(failed!=0)throw new IllegalStateException("UnexpectedFinalVersion");
                try(var s=c.createStatement()){
                    s.executeQuery("SELECT `row_number` FROM sys_import_job_row_result WHERE 1=0").close();
                    s.executeQuery("SELECT `row_number` FROM sys_student_import_row WHERE 1=0").close();
                }
                System.out.println("migrate.latestVersion="+latest);System.out.println("migrate.failedRows="+failed);System.out.println("migrate.tables="+tables);
                if(!configured)Files.writeString(backup.resolve("clone-migration-verified.properties"),
                    "schema="+schema+"\nversion="+latest+"\nvalidation=true\ntables="+tables+"\n");
            }
        }catch(Exception e){
            System.out.println("failure.stage="+stage);System.out.println("failure.type="+e.getClass().getSimpleName());
            Throwable cause=e;
            while(cause!=null){
                if(cause instanceof SQLException sql){System.out.println("failure.sqlState="+sql.getSQLState());System.out.println("failure.sqlCode="+sql.getErrorCode());break;}
                cause=cause.getCause();
            }
            System.exit(2);
        }
    }
}
