package com.snhu.sslserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.MessageDigest;

@SpringBootApplication
public class SslServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SslServerApplication.class, args);
	}

}

@RestController
class ServerController{
    @RequestMapping("/hash")
    public String myHash() throws Exception {
        String name = "Jarrale Butts!";

        // SHA-256 digest of the data value, returned as a lowercase hex string
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(name.getBytes());
        byte[] hash = md.digest();
        String hexToHash = bytesToHex(hash);

        return "<p>Data: " + name + "<br>SHA-256 checksum: " + hexToHash + "</p>";
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}