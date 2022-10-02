
package me.jumpwatch.webserver.utils;
        import me.jumpwatch.webserver.WebCore;
        import org.bukkit.plugin.java.JavaPlugin;
        import javax.crypto.KeyGenerator;
        import javax.crypto.SecretKey;
        import java.io.*;
        import java.security.*;
        import java.security.cert.Certificate;
        import java.security.cert.CertificateException;
        import java.util.Base64;
        import java.util.Random;
        import java.util.UUID;

public class AutoJKS {
    public static SecretKey generateKey(int n) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
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
        SecretKey secretKey = generateKey(128);
        KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(secretKey);
        ks.setEntry("keyalias2", secretKeyEntry, entryPassword);
        FileOutputStream fos = new FileOutputStream(main.getDataFolder() + "/ssl/" + name + ".jks");
        ks.store(fos, password);
        fos.close();
    }



    public static void makeRSAkey() throws NoSuchAlgorithmException {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        main.getLogger().info("This is your PRIVATE & PUBLIC KEY!");
        main.getLogger().info("-----BEGIN PRIVATE KEY-----");
        main.getLogger().info(Base64.getMimeEncoder().encodeToString( kp.getPrivate().getEncoded()));
        main.getLogger().info("-----END PRIVATE KEY-----");
        main.getLogger().info("-----BEGIN PUBLIC KEY-----");
        main.getLogger().info(Base64.getMimeEncoder().encodeToString( kp.getPublic().getEncoded()));
        main.getLogger().info("-----END PUBLIC KEY-----");
        main.getLogger().info("Private Key is saved in the SSL folder: " + main.getDataFolder() + "/ssl/privkey.pem");
        main.getLogger().info("Public Key is saved in the SSL folder: " + main.getDataFolder() + "/ssl/publkey.pem");
        Writer privkey = null;
        Writer publkey = null;

        try {
            privkey = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream( main.getDataFolder() + "/ssl/privkey.pem"), "utf-8"));
            privkey.write("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString( kp.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        } catch (IOException ex) {
            // Report
        } finally {
            try {privkey.close();} catch (Exception ex) {/*ignore*/}
        }


        try {
            publkey = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(main.getDataFolder() + "/ssl/publkey.pem"), "utf-8"));
            publkey.write("-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString( kp.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
        } catch (IOException ex) {
            // Report
        } finally {
            try {publkey.close();} catch (Exception ex) {/*ignore*/}
        }
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