package org.wildfly.swarm.jaxrs;

import java.nio.charset.StandardCharsets;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.jose.Base64Url;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.jose.Jose;
import org.wildfly.swarm.jose.JoseLookup;

@RunWith(Arquillian.class)
public class JoseCompactTest {

    @Deployment
    public static Archive<?> createDeployment() throws Exception {
        JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);
        deployment.addResource(MyResource.class);
        deployment.addResource(SecuredApplication.class);
        deployment.addResource(JoseExceptionMapper.class); 
        deployment.addAllDependencies();
        deployment.addAsResource("keystore.jks");
        deployment.addAsResource("project-defaults.yml");
        return deployment;
    }

    @Test
    public void testJwsCompact() throws Exception {
        Jose jose = JoseLookup.lookup().get();
        String signedData = ClientBuilder.newClient().target("http://localhost:8080/sign")
                                .request(MediaType.TEXT_PLAIN)
                                .post(Entity.entity(jose.sign("Hello"), MediaType.TEXT_PLAIN),
                                      String.class);
        Assert.assertEquals("Hello", jose.verify(signedData));
    }
    
    @Test
    public void testJwsCompactTampered() throws Exception {
        String[] jwsParts = JoseLookup.lookup().get().sign("Hello").split("\\.");
        Assert.assertEquals(3, jwsParts.length);
        String encodedContent = jwsParts[1];
        Assert.assertEquals("Hello", new String(Base64Url.decode(encodedContent), StandardCharsets.UTF_8));
        String newEncodedContent = Base64Url.encode("HellO".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals("HellO", new String(Base64Url.decode(newEncodedContent), StandardCharsets.UTF_8));
        // Headers + content + signature
        String newJws = jwsParts[0] + "." + newEncodedContent + "." + jwsParts[2];  
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), 
            ClientBuilder.newClient().target("http://localhost:8080/sign")
               .request(MediaType.TEXT_PLAIN)
               .post(Entity.entity(newJws, MediaType.TEXT_PLAIN))
               .getStatus());
    }
       
    @Test
    public void testJwsJweCompact() throws Exception {
        Jose jose = JoseLookup.lookup().get();
        String signedAndEncryptedData = ClientBuilder.newClient().target("http://localhost:8080/signAndEncrypt")
                                   .request(MediaType.TEXT_PLAIN)
                                   .post(Entity.entity(jose.encrypt(jose.sign("Hello")), MediaType.TEXT_PLAIN),
                                         String.class);
        Assert.assertEquals("Hello", jose.verify(jose.decrypt(signedAndEncryptedData)));
    }
    
    @Test
    public void testJweCompact() throws Exception {
        Jose jose = JoseLookup.lookup().get();
        String encryptedData = ClientBuilder.newClient().target("http://localhost:8080/encrypt")
                                   .request(MediaType.TEXT_PLAIN)
                                   .post(Entity.entity(jose.encrypt("Hello"), MediaType.TEXT_PLAIN),
                                         String.class);
        Assert.assertEquals("Hello", jose.decrypt(encryptedData));
    }
    
    @Test
    public void testJweCompactTampered() throws Exception {
        Jose jose = JoseLookup.lookup().get();
        String[] jweParts = jose.encrypt("Hello").split("\\.");
        Assert.assertEquals(5, jweParts.length);
        String[] newJweParts = jose.encrypt("HellO").split("\\.");
        // Headers + IV + Encrypted CEK + Cipher + Authentication Tag 
        String newJwe = jweParts[0] + "." + jweParts[1] + "." + jweParts[2] + "."
                + newJweParts[3] + "." + jweParts[4]; 
        
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), 
            ClientBuilder.newClient().target("http://localhost:8080/encrypt")
               .request(MediaType.TEXT_PLAIN)
               .post(Entity.entity(newJwe, MediaType.TEXT_PLAIN))
               .getStatus());
    }
}