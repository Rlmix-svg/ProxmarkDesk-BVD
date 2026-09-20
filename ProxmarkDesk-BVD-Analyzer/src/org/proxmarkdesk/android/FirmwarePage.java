// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import java.io.*;

final class FirmwarePage {
    static final int PICK=41;
    private final MainActivity a;
    private boolean importing;
    FirmwarePage(MainActivity a){this.a=a;}
    void open() {
        if(!a.ready())return;
        new AlertDialog.Builder(a).setTitle("Обновить Proxmark3 Easy")
            .setMessage(a.service.firmwareUpdating.get()?a.service.firmwareStatus:
                "Выберите пакет PM3GENERIC / AT91SAM7S версии встроенного клиента.\n\nОбычный режим автоматически обновляет bootrom, ждёт повторного появления USB, обновляет fullimage и проверяет hw version. Режим «только bootrom» оставлен для восстановления.")
            .setPositiveButton("Выбрать пакет",(d,w)->pick())
            .setNeutralButton("Экспорт журнала",(d,w)->{File f=a.service.firmwareLog();if(f.isFile())a.exportFile(f);else a.message("Журнала обновления пока нет");})
            .setNegativeButton("Закрыть",null).show();
    }
    private void pick(){
        if(a.service==null||a.service.firmwareUpdating.get()||importing){a.message("Дождитесь текущей операции");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE);
        a.startActivityForResult(i,PICK);
    }
    void importPackage(Uri uri){
        if(importing||!a.ready()||a.service.firmwareUpdating.get())return;
        importing=true;Toast.makeText(a,"Проверка пакета прошивки…",Toast.LENGTH_SHORT).show();
        new Thread(()->{try{
            FirmwarePackage pack=FirmwarePackage.read(a.getContentResolver().openInputStream(uri),new File(a.getFilesDir(),"firmware"));
            a.runOnUiThread(()->{importing=false;if(!a.isFinishing()&&!a.isDestroyed())confirm(pack);});
        }catch(Exception e){a.runOnUiThread(()->{importing=false;if(!a.isFinishing()&&!a.isDestroyed())a.message("Пакет не принят: "+e.getMessage());});}},"firmware-import").start();
    }
    private void confirm(FirmwarePackage pack){
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(a.dp(20),0,a.dp(20),0);
        CheckBox boot=a.themedCheckBox();boot.setText("Восстановление: обновить только bootrom");boot.setEnabled(pack.bootrom!=null);box.addView(boot);
        CheckBox device=a.themedCheckBox();device.setText("Подключён Proxmark3 Easy с AT91SAM7S; пакет получен из доверенной сборки PM3GENERIC");box.addView(device);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Прошивка "+pack.version)
            .setMessage("Обычный режим: bootrom → ожидание USB → fullimage → проверка hw version. SHA-256 и структура ELF проверены; происхождение пакета подтверждается отдельно.\n\nНе отключайте USB и не закрывайте приложение до завершения.")
            .setView(box).setPositiveButton("Продолжить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!device.isChecked()){a.message("Подтвердите модель устройства и происхождение пакета");return;}
            final boolean bootOnly=boot.isChecked();dialog.dismiss();
            if(bootOnly)new AlertDialog.Builder(a).setTitle("Только bootrom?")
                .setMessage("Режим восстановления запишет только загрузчик. Основная прошивка изменена не будет.")
                .setPositiveButton("Записать bootrom",(x,w)->start(pack,true)).setNegativeButton("Отмена",null).show();
            else new AlertDialog.Builder(a).setTitle("Начать полное обновление?")
                .setMessage("Будут последовательно обновлены bootrom и fullimage. Между этапами приложение дождётся повторного появления USB и в конце проверит обе версии через hw version.")
                .setPositiveButton("Обновить",(x,w)->start(pack,false)).setNegativeButton("Отмена",null).show();
        }));dialog.show();
    }
    private void start(FirmwarePackage pack,boolean bootOnly){
        a.guard(()->{if(!a.ready())return;boolean root=a.rootMode==null?a.prefs().getBoolean("root",true):a.rootMode.isChecked();if(!root){a.message("Для обновления включите root-доступ к USB");return;}
            String port=a.port==null?a.prefs().getString("port","/dev/ttyACM0"):a.port.getText().toString().trim();++a.connectGeneration;a.autoConnecting=false;
            a.startForegroundService(new Intent(a,ClientService.class));a.service.updateFirmware(pack,port,bootOnly);a.page(3);
        });
    }
}
