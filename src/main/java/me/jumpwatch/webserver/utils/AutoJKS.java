
package me.jumpwatch.webserver.utils;
        import me.jumpwatch.webserver.WebCore;
        import org.bukkit.plugin.java.JavaPlugin;
        import javax.crypto.KeyGenerator;
        import javax.crypto.SecretKey;
        import java.io.*;
        import java.security.*;
        import java.security.cert.Certificate;
        import java.security.cert.CertificateException;
        import java.util.Random;
        import java.util.UUID;

public class AutoJKS {
    //new
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();
        return pair;
    }





    //old
    public static SecretKey generateKey(int n) throws NoSuchAlgorithmException {
        KeyPairGenerator keyGenerator = KeyPairGenerator .getInstance("RSA");
        keyGenerator.initialize(2048);
        keyGenerator.init(n);
        SecretKey originalKey = keyGenerator.generateKey();
        return originalKey;
    }
    public static void makeJKS() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableEntryException {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        String pass = generateRandomPassword(28);
        char[] password = pass.toCharArray();
        String name = generateString();
        ConfigChanger.Changeconf(main.getDataFolder() + "/config.yml", "filename", name);
        ConfigChanger.Changeconf(main.getDataFolder() + "/config.yml", "filepass", pass.replace("[", "").replace("]", ""));
        ConfigChanger.Changeconf(main.getDataFolder() + "/config.yml", "filekey", pass.replace("[", "").replace("]", ""));
        ConfigChanger.Changeconf(main.getDataFolder() + "/config.yml", "Autokey: true", "Autokey: false");
        main.getLogger().info("JKS File name: " + name);
        main.getLogger().info("JKS File password: " + pass.toString().replace("[", "").replace("]", ""));
        ks.load(null, password);
        KeyStore.ProtectionParameter entryPassword = new KeyStore.PasswordProtection(password);
        KeyStore.Entry keyEntry = ks.getEntry("keyAlias", entryPassword);
        SecretKey secretKey = generateKey(2048);
        KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(secretKey);
        ks.setEntry("keyalias2", secretKeyEntry, entryPassword);
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