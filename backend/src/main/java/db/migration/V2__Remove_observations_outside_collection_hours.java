package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.TimeZone;
import org.slf4j.LoggerFactory;

public class V2__Remove_observations_outside_collection_hours extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        for (String table : new String[]{"wait_time_snapshot", "collection_run"}) {
            int removed = 0;
            try (PreparedStatement select = context.getConnection().prepareStatement("SELECT id, collection_slot FROM " + table); PreparedStatement delete = context.getConnection().prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        Timestamp slot = rows.getTimestamp("collection_slot", utc);
                        if (slot.toInstant().atZone(ZoneId.of("Asia/Riyadh")).getHour() < 16) {
                            delete.setLong(1, rows.getLong("id"));
                            delete.addBatch();
                            removed++;
                            if (removed % 500 == 0) {
                                delete.executeBatch();
                            }
                        }
                    }
                }
                delete.executeBatch();
            }
            LoggerFactory.getLogger(getClass()).info("Collection window cleanup removed {} rows from {} outside 16:00–00:00 Asia/Riyadh", removed, table);
        }
    }
}
