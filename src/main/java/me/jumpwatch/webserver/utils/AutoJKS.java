
/*
 * ******************************************************
 *  *Copyright (c) 2020-2022. Jesper Henriksen mhypers@gmail.com
 *
 *  * This file is part of WebServer project
 *  *
 *  * WebServer can not be copied and/or distributed without the express
 *  * permission of Jesper Henriksen
 *  ******************************************************
 */ 
package me.jumpwatch.webserver.utils;


public class AutoJKS {
////    private static String pass = null;
////
////
////
////    public static void makeRSAkey() throws NoSuchAlgorithmException, IOException {
////        WebCore main = JavaPlugin.getPlugin(WebCore.class);
////
////        // Generate the RSA key pair
////        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
////        kpg.initialize(2048);
////        KeyPair kp = kpg.generateKeyPair();
////
////        // Save the private key in PEM format
////        try (PEMWriter privkeyWriter = new PEMWriter(new OutputStreamWriter(
////                new FileOutputStream(main.getDataFolder() + "/ssl/privkey.pem"), StandardCharsets.UTF_8))) {
////            privkeyWriter.writeObject(kp.getPrivate());
////        }
////
////        // Save the public key in PEM format
////        try (PEMWriter publkeyWriter = new PEMWriter(new OutputStreamWriter(
////                new FileOutputStream(main.getDataFolder() + "/ssl/publkey.pem"), StandardCharsets.UTF_8))) {
////            publkeyWriter.writeObject(kp.getPublic());
////        }
////
////        main.getLogger().info("Private Key is saved in the SSL folder: " + main.getDataFolder() + "/ssl/privkey.pem");
////        main.getLogger().info("Public Key is saved in the SSL folder: " + main.getDataFolder() + "/ssl/publkey.pem");
////        main.getLogger().info("Private Key algorithm: " + kp.getPrivate().getAlgorithm());
////        main.getLogger().info("Public Key algorithm: " + kp.getPublic().getAlgorithm());
////    }
////    public static void convertKey(String folderPath) throws Exception {
////        WebCore main = JavaPlugin.getPlugin(WebCore.class);
////        // Load the private key from the private key .pem file
////        try (FileReader fileReader = new FileReader(folderPath + "/privkey.pem");
////             PEMParser pemParser = new PEMParser(fileReader)) {
////            main.getLogger().info("Found priv key!");
////            Object pemObject = pemParser.readObject();
////            main.getLogger().info("Trying to check if key is a pemkeypair: " + (pemObject instanceof PEMKeyPair));
////            if (pemObject instanceof PEMKeyPair) {
////                PEMKeyPair pemKeyPair = (PEMKeyPair) pemObject;
////                KeyPair keyPair = new JcaPEMKeyConverter().getKeyPair(pemKeyPair);
////                main.getLogger().info("Is PEMKeyPair");
////
////                // Load the public key from the public key .pem file
////                try (FileReader pubKeyFileReader = new FileReader(folderPath + "/publkey.pem");
////                     PEMParser pubKeyPemParser = new PEMParser(pubKeyFileReader)) {
////
////                    main.getLogger().info("Found publ key!");
////                    SubjectPublicKeyInfo publicKeyInfo = ((PEMKeyPair) pemObject).getPublicKeyInfo();
////
////                    // Create an X.509 certificate from the SubjectPublicKeyInfo
////                    X509CertificateHolder certHolder = new X509CertificateHolder(publicKeyInfo.getEncoded());
////
////                    X509Certificate cert = new JcaX509CertificateConverter()
////                            .setProvider(new BouncyCastleProvider())
////                            .getCertificate(certHolder);
////
////                    main.getLogger().info("Create a Java KeyStore and store the private key and certificate");
////                    // Create a Java KeyStore and store the private key and certificate
////                    KeyStore keyStore = KeyStore.getInstance("JKS");
////                    keyStore.load(null, null); // Initialize the KeyStore
////                    pass = generateRandomPassword(16);
////                    // Set a password for the KeyStore (change this to your desired password)
////                    char[] password = pass.toCharArray();
////                    main.getLogger().info("Password " + pass);
////
////                    // Add the private key and certificate to the KeyStore
////                    keyStore.setKeyEntry("alias", keyPair.getPrivate(), password, new Certificate[]{cert});
////
////                    // Save the KeyStore to a .jks file
////                    try (FileOutputStream fos = new FileOutputStream(main.getDataFolder() + "/ssl/" + "test.jks")) {
////                        keyStore.store(fos, password);
////                        main.getLogger().info(fos.toString());
////                    }
////                    // Making sure the password is removed as soon as we are done with it.
////                    pass = null;
////                }
////            }
////        }
//    }
//
//
//    public static String generateRandomPassword(int len) {
//        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi"
//                +"jklmnopqrstuvwxyz!@#$%&";
//        Random rnd = new Random();
//        StringBuilder sb = new StringBuilder(len);
//        for (int i = 0; i < len; i++)
//            sb.append(chars.charAt(rnd.nextInt(chars.length())));
//        return sb.toString();
//    }

}