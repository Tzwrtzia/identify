package sslify;

import com.eaio.uuid.UUID;
import com.google.common.base.Joiner;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.bouncycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.Vector;

public class X509CertificateGenerator {
    private static final String
            PROPS_HOURS_BEFORE = "hours.before",
            PROPS_HOURS_AFTER = "hours.after",
            SIGNATURE_ALGORITHM = "SHA1withRSA",
            CA_CERT_PATH = "ca.cert.path",
            CA_KEY_PATH = "ca.cert.path",
            GROUP_PREFIX = "group:";

    /* TODO: initialize */
    private ConfigProperties props;
    private X509Certificate caCert;
    private PrivateKey caPrivateKey;

    public X509CertificateGenerator() throws FileNotFoundException {
        props = ConfigProperties.getProperties(ConfigProperties.X509);
        Security.addProvider(new BouncyCastleProvider());

    }

    private static PrivateKey readPrivateKey throws FileNotFoundException {
        File caPrivateKeyFile = new File(props.getProperty(CA_KEY_PATH));
        FileReader caPrivateKeyFileReader = null;
        caPrivateKeyFileReader = new FileReader(caPrivateKeyFile);
        PEMReader caPrivateKeyReader = new PEMReader(caPrivateKeyFile, EmptyPasswordFinder.getInstance());
        try {
            KeyPair key = (KeyPair) caPrivateKeyReader.readObject();
            return key.getPrivate();
        } finally {
            caPrivateKeyReader.close();
            caPrivateKeyFileReader.close();
        }
    }

    public static java.security.cert.X509Certificate createCert(final String user) {
        File caPrivateKeyFile = new File(props.getProperty(CA_KEY_PATH));
        FileReader caPrivateKeyFileReader = new FileReader(caPrivateKeyFile);
        PEMReader caPrivateKeyReader = new PEMReader(caPrivateKeyFile, EmptyPasswordFinder.getInstance());
        try {
            KeyPair key = (KeyPair) caPrivateKeyReader.readObject();
            return key.getPrivate();
        } finally {
            caPrivateKeyReader.close();
            caPrivateKeyFileReader.close();
        }

        File caCertFile = new File(props.getProperty(CA_CERT_PATH));
        FileReader caCertFileReader = new FileReader(caCertFile);
        try {
            return (KeyPair) r.readObject();
        } catch (IOException ex) {
            throw new IOException("The private key could not be decrypted", ex);
        } finally {
            r.close();
            fileReader.close();
        }

        final UUID uuid = new UUID();
        final X509V3CertificateGenerator generator = new X509V3CertificateGenerator();

        final CertInfo infos = CertInfo.fromLDAP(user);
        final SshPublicKey sshKey = SshPublicKey.fromRepo(user);

        final Calendar calendar = Calendar.getInstance();
        final int hoursBefore = Integer.parseInt(props.getProperty(PROPS_HOURS_BEFORE));
        final int hoursAfter = Integer.parseInt(props.getProperty(PROPS_HOURS_AFTER));

        final Vector<DERObjectIdentifier> attrsVector = new Vector<DERObjectIdentifier>();
        final Hashtable<DERObjectIdentifier, String> attrsHash = new Hashtable<DERObjectIdentifier, String>();

        attrsHash.put(X509Principal.CN, infos.getCn());
        attrsVector.add(X509Principal.CN);

        attrsHash.put(X509Principal.UID, infos.getUid());
        attrsVector.add(X509Principal.UID);

        attrsHash.put(X509Principal.EmailAddress, infos.getMail());
        attrsVector.add(X509Principal.EmailAddress);

        attrsHash.put(X509Principal.OU, Joiner.on('+').join(infos.getGroups()));
        attrsVector.add(X509Principal.OU);

        generator.setSubjectDN(new X509Principal(attrsVector, attrsHash));

        calendar.add(Calendar.HOUR, -hoursBefore);
        generator.setNotBefore(calendar.getTime());

        calendar.add(Calendar.HOUR, hoursBefore + hoursAfter);
        generator.setNotAfter(calendar.getTime());

        // Reuse the UUID time as a SN
        generator.setSerialNumber(new BigInteger(new Long(uuid.getTime()).toString()));

        generator.addExtension(X509Extensions.AuthorityKeyIdentifier, false,
                new AuthorityKeyIdentifierStructure(caCert));

        generator.addExtension(X509Extensions.SubjectKeyIdentifier, false,
                new SubjectKeyIdentifierStructure(sshKey.getKey()));

        // Store the UUID
        generator.addExtension(X509Extensions.IssuingDistributionPoint, false,
                new DEROctetString(uuid.toString().getBytes()));

        // Not a CA
        generator.addExtension(X509Extensions.BasicConstraints, true,
                new BasicConstraints(false));

        generator.setIssuerDN(caCert.getSubjectX500Principal());
        generator.setPublicKey(sshKey.getKey());
        generator.setSignatureAlgorithm(SIGNATURE_ALGORITHM);

        final java.security.cert.X509Certificate cert = generator.generate(caPrivateKey, "BC");

        cert.checkValidity();
        cert.verify(caCert.getPublicKey());

        return cert;
    }

    private static class EmptyPasswordFinder implements PasswordFinder {
        private static EmptyPasswordFinder singleton = new EmptyPasswordFinder();

        public char[] getPassword() {
            return new char[0];
        }

        public static EmptyPasswordFinder getInstance() {
            return singleton;
        }
    }
}