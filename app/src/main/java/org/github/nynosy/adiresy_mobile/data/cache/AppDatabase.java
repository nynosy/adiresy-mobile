package org.github.nynosy.adiresy_mobile.data.cache;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                AddressEntity.class,
                AdminUnitEntity.class,
                SearchHistoryEntity.class,
                BookmarkListEntity.class,
                BookmarkEntity.class
        },
        version = 3,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract AddressDao      addressDao();
    public abstract AdminUnitDao    adminUnitDao();
    public abstract SearchHistoryDao searchHistoryDao();
    public abstract BookmarkListDao bookmarkListDao();
    public abstract BookmarkDao     bookmarkDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmark_lists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`description` TEXT, " +
                "`emoji` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL)"
            );
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`canonical_code` TEXT NOT NULL, " +
                "`latitude` REAL NOT NULL, " +
                "`longitude` REAL NOT NULL, " +
                "`fokontany_name` TEXT, " +
                "`commune_name` TEXT, " +
                "`district_name` TEXT, " +
                "`region_name` TEXT, " +
                "`user_description` TEXT, " +
                "`list_id` INTEGER NOT NULL, " +
                "`saved_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`list_id`) REFERENCES `bookmark_lists`(`id`) ON DELETE CASCADE)"
            );
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_bookmarks_list_id` " +
                "ON `bookmarks` (`list_id`)"
            );
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_canonical_code` " +
                "ON `bookmarks` (`canonical_code`)"
            );
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `name` TEXT");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "adiresy.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return instance;
    }
}
