package tv.vela.launcher;

import android.database.sqlite.SQLiteDatabase;
import java.util.function.Consumer;

/** Apply index and column changes once, including when two launcher processes open an old database. */
final class VelaDatabase {
    private VelaDatabase(){}

    static void ensureSchema(SQLiteDatabase db,int version,Consumer<SQLiteDatabase> migrate){
        if(db.getVersion()>=version)return;
        db.beginTransaction();
        try{
            if(db.getVersion()<version){
                migrate.accept(db);
                db.setVersion(version);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
