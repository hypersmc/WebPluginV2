package me.jumpwatch.webserver.utils;

import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Random;
import java.util.UUID;

public class AutoJKS {

    public static void makeJKS() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        char[] password = generateRandomPassword(28).toCharArray();
        String name = generateString();

        ConfigChanger.Changeconf(main.getDataFolder() + "/config.yml", "\"filename\"", name);
        ConfigChanger.Changeconf(main.getDataFolder() + "/config.yml", "\"filepass\"", password.toString());
        ConfigChanger.Changeconf(main.getDataFolder() + "/config.yml", "\"filekey\"", password.toString());
        ConfigChanger.Changeconf(main.getDataFolder() + "/config.yml", "Autokey: true", "Autokey: false");
        main.getLogger().info("JKS File name: " + name);
        main.getLogger().info("JKS File password: " + password.toString());
        ks.load(null, password);

        FileOutputStream fos = new FileOutputStream(main.getDataFolder() + "/ssl/" + name + ".jks");
        ks.store(fos, password);
        fos.close();

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
        return uuid;
    }

}
