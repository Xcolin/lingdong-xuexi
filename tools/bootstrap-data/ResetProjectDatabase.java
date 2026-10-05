import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Explicit reset of the configured project schema only. Never called by application startup. */
public class ResetProjectDatabase {
    static Map<?,?> map(Object value) { return value instanceof Map<?,?> m ? m : Map.of(); }
    static String resolve(Object value) {
        String s=Objects.toString(value, "");
        if(s.startsWith("${") && s.endsWith("}")) {
            String[] parts=s.substring(2,s.length()-1).split(":",2);
            return System.getenv().getOrDefault(parts[0],parts.length>1?parts[1]:"");
        }
        return s;
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3 || !Set.of("inspect","RESET-lingdong_learning").contains(args[2]))
            throw new IllegalArgumentException("Usage: config credentials inspect|RESET-lingdong_learning");
        var config=map(new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(Path.of(args[0]))));
        var ds=map(map(config.get("spring")).get("datasource"));
        String url=resolve(ds.get("url")), user=resolve(ds.get("username")), password=resolve(ds.get("password"));
        var credentials=new Properties();
        try(var input=Files.newInputStream(Path.of(args[1]))) { credentials.load(input); }
        var placeholders=new HashMap<String,String>();
        for(String name:List.of("admin","auditor","orgadmin","teacher01","teacher02","parent01","parent02")) {
            String hash=credentials.getProperty(name+"Hash");
            if(hash==null || !hash.startsWith("$2")) throw new IllegalStateException("Missing existing password hash");
            placeholders.put(name+"Hash",hash);
        }
        // Both URL and server-selected catalog must refer to this exact authorized schema.
        if(!url.matches("jdbc:mysql://[^/]+/lingdong_learning(?:\\?.*)?")) throw new IllegalStateException("Unexpected configured schema");
        try(var c=DriverManager.getConnection(url,user,password)) {
            if(!"lingdong_learning".equals(c.getCatalog())) throw new IllegalStateException("Unexpected connected schema");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
                r.next();System.out.println("target.schema=lingdong_learning tables="+r.getInt(1));
            }
        }
        if(args[2].equals("inspect")) return;
        var main=Flyway.configure().dataSource(url,user,password).schemas("lingdong_learning")
            .locations("filesystem:lingdong-bansui-server/src/main/resources/db/migration").cleanDisabled(false).load();
        main.clean();
        System.out.println("reset.completed=true");
        var migrated=main.migrate();
        if(!main.validateWithResult().validationSuccessful) throw new IllegalStateException("Main migration validation failed");
        System.out.println("main.migrationsExecuted="+migrated.migrationsExecuted);
        var demo=Flyway.configure().dataSource(url,user,password).table("flyway_demo_history")
            .locations("filesystem:tools/bootstrap-data/migrations").baselineOnMigrate(true).baselineVersion("0")
            .placeholders(placeholders).cleanDisabled(true).load();
        demo.migrate();
        if(!demo.validateWithResult().validationSuccessful) throw new IllegalStateException("Demo migration validation failed");
        try(var c=DriverManager.getConnection(url,user,password)) {
            for(String table:List.of("sys_user","sys_organization","sys_role","sys_permission","sys_menu","sys_user_role","edu_student"))
                try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)) {r.next();System.out.println(table+".count="+r.getLong(1));}
        }
        System.out.println("initialization.completed=true");
    }
}
