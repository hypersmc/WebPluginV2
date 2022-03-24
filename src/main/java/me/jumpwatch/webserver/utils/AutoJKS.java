package me.jumpwatch.webserver.utils;

import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;

public class AutoJKS {

    public static void makeJKS() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        char[] password = generateRandomPassword(28).toCharArray();
        String name = generateString();
        main.getConfig().set("SSLSettings.SSLJKSName", name);
        main.getConfig().set("SSLSettings.SSLJKSPass", password);
        main.getConfig().set("SSLSettings.SSLJKSKey", password);
        main.reloadConfig();
        main.saveConfig();
        ks.load(null, password);

        FileOutputStream fos = new FileOutputStream(main.getDataFolder() + "/ssl/" + name);
        ks.store(fos, password);
        fos.close();
        main.getConfig().set("Settings.Autokey", false);
    }



    public static String generateRandomPassword(int len) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi"
                +"jklmnopqrstuvwxyz!@#$%&";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++)
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
    public static String generateString() {
        String uuid = UUID.randomUUID().toString();
        return "uuid = " + uuid;
    }

}
