package me.jumpwatch.webserver.utils;

import me.jumpwatch.webserver.WebCore;
import org.bouncycastle.openssl.PEMWriter;
import org.bukkit.plugin.java.JavaPlugin;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.*;
import java.security.KeyPair;
import java.security.cert.CertificateEncodingException;
import java.util.Optional;


/**
 * @author JumpWatch on 15-01-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class LetsEncryptCertificateMaker {



    public static void generateLetsEncryptCertificate(String domain) {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        ConfigChanger.Changeconf(main.getDataFolder() + "/config.yml", "Autokey: true", "Autokey: false");
        try {
            // Create a KeyPair for your domain
            KeyPair domainKeyPair = KeyPairUtils.createKeyPair(2048);

            // Start a session with the Let's Encrypt ACME server
            Session session = new Session("acme://letsencrypt.org/staging"); // Use staging for testing

            // Create a new registration
            Account account = new AccountBuilder()
                    .agreeToTermsOfService()
                    .useKeyPair(domainKeyPair)
                    .create(session);

            // Authorize your domain
            Authorization auth = account.preAuthorizeDomain(domain);

            // Get the HTTP challenge
            Optional<Http01Challenge> httpChallenge = auth.findChallenge(Http01Challenge.TYPE);
            if (httpChallenge.isPresent()) {
                Http01Challenge challenge = httpChallenge.get();

                // Perform the HTTP challenge (Make sure your server is accessible from the internet on port 80)
                challenge.trigger();

                // Wait for the challenge to be valid
                challenge.wait();

                if (challenge.getStatus() != Status.VALID) {
                    System.out.println("Challenge did not pass, exiting.");
                    return;
                }

                // Generate a CSR for the certificate
                CSRBuilder csrb = new CSRBuilder();
                csrb.addDomain(domain);
                csrb.sign(domainKeyPair);

                // Request the certificate
                Order order = account.newOrder().domains(domain).create();
                order.execute(csrb.getEncoded());

                // Download the certificate
                Certificate certificate = order.getCertificate();

                // Save the certificate and private key to files
                saveToPemFile("private-key.pem", domainKeyPair.getPrivate());
                saveToPemFile("public-key.pem", certificate.getCertificate().getEncoded());

                System.out.println("Let's Encrypt certificate obtained successfully!");
            } else {
                System.out.println("HTTP challenge not found, exiting.");
            }

            // Close the session manually
            session.connect().close();

        } catch (IOException | AcmeException e) {
            e.printStackTrace();
        } catch (InterruptedException | CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    private static void saveToPemFile(String filename, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(filename);
             PEMWriter pemWriter = new PEMWriter(new OutputStreamWriter(fos))) {
            pemWriter.writeObject(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveToPemFile(String filename, Object object) {
        try (FileOutputStream fos = new FileOutputStream(filename);
             PEMWriter pemWriter = new PEMWriter(new OutputStreamWriter(fos))) {
            pemWriter.writeObject(object);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
