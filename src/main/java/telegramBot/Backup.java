package telegramBot;

import admin.AdminsData;

import java.io.File;
import java.time.LocalDateTime;

public class Backup extends Thread{


    @Override
    public void run() {
        try {
            sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        LocalDateTime nextBackupTime = LocalDateTime.now().minusHours(1);

        while (true){
            if (LocalDateTime.now().isAfter(nextBackupTime)){

                AllWordBase.backupAllWord();
                Main.backupUserMapAndAdmin();
                AdminsData.backupUserMapAndAdmin();

                nextBackupTime = LocalDateTime.now().plusMinutes(15);

                try {
                    sleep(60000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }
}
