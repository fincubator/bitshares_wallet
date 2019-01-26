package com.bitshares.bitshareswallet.wallet;

import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.bitshares.bitshareswallet.wallet.fc.crypto.sha256_object;
import com.bitshares.bitshareswallet.wallet.fc.crypto.sha512_object;
import com.bitshares.bitshareswallet.wallet.graphene.chain.compact_signature;
import com.mrd.bitlib.crypto.InMemoryPrivateKey;
import com.mrd.bitlib.crypto.RandomSource;
import com.mrd.bitlib.crypto.SignedMessage;
import com.mrd.bitlib.crypto.ec.EcTools;
import com.mrd.bitlib.crypto.ec.Parameters;
import com.mrd.bitlib.crypto.ec.Point;
import com.mrd.bitlib.util.Sha256Hash;

import org.bitcoinj.core.ECKey;
import org.spongycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.spongycastle.jce.spec.ECNamedCurveParameterSpec;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;

public class private_key {
    private byte[] key_data = new byte[32];
    public private_key(byte key[]) {
        System.arraycopy(key, 0, key_data, 0, key_data.length);
    }

    public byte[] get_secret() {
        return key_data;
    }

    public static private_key generate() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDsA", "SC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
            keyGen.initialize(ecSpec, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();
            return new private_key(keyPair);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }

        return null;
    }

    public public_key get_public_key() {
        try {
            ECNamedCurveParameterSpec secp256k1 = org.spongycastle.jce.ECNamedCurveTable.getParameterSpec("secp256k1");
            org.spongycastle.jce.spec.ECPrivateKeySpec privSpec = new org.spongycastle.jce.spec.ECPrivateKeySpec(new BigInteger(1, key_data), secp256k1);
            KeyFactory keyFactory = KeyFactory.getInstance("EC","SC");

            byte[] keyBytes = new byte[33];
            System.arraycopy(key_data, 0, keyBytes, 1, 32);
            BigInteger privateKeys = new BigInteger(keyBytes);
            BCECPrivateKey privateKey = (BCECPrivateKey) keyFactory.generatePrivate(privSpec);

            Point Q = EcTools.multiply(Parameters.G, privateKeys);

            //ECPoint ecPoint = ECKey.CURVE.getG().multiply(privateKeys);
            org.spongycastle.math.ec.ECPoint ecpubPoint = new org.spongycastle.math.ec.custom.sec.SecP256K1Curve().createPoint(Q.getX().toBigInteger(), Q.getY().toBigInteger());
            PublicKey publicKey = keyFactory.generatePublic(new org.spongycastle.jce.spec.ECPublicKeySpec(ecpubPoint, secp256k1));

            BCECPublicKey bcecPublicKey = (BCECPublicKey)publicKey;
            byte bytePublic[] = bcecPublicKey.getQ().getEncoded(true);

            return new public_key(bytePublic);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    private private_key(KeyPair ecKey){
        BCECPrivateKey privateKey = (BCECPrivateKey) ecKey.getPrivate();
        byte[] privateKeyGenerate = privateKey.getD().toByteArray();
        if (privateKeyGenerate.length == 33) {
            System.arraycopy(privateKeyGenerate, 1, key_data, 0, key_data.length);
        } else {
            System.arraycopy(privateKeyGenerate, 0, key_data, 0, key_data.length);
        }
    }

    public compact_signature sign_compact(sha256_object digest, boolean require_canonical ) {
        compact_signature signature = null;

        SecureRandom secureRandom = new SecureRandom();
        secureRandom.setSeed(generateSeed());
        RandomSource randomSource = secureRandom::nextBytes;

        while (true) {
            Log.w("HmacPRNG", "CYCLE");
            InMemoryPrivateKey inMemoryPrivateKey = new InMemoryPrivateKey(key_data);
            SignedMessage signedMessage = inMemoryPrivateKey.signHash(new Sha256Hash(digest.hash), randomSource);
            byte[] byteCompact = signedMessage.bitcoinEncodingOfSignature();
            signature = new compact_signature(byteCompact);

            boolean bResult = public_key.is_canonical(signature);
            if (bResult) {
                break;
            }
        }

        return signature;
    }

    private static final byte[] BUILD_FINGERPRINT_AND_DEVICE_SERIAL = getBuildFingerprintAndDeviceSerial();

    private static byte[] getBuildFingerprintAndDeviceSerial() {
        StringBuilder result = new StringBuilder();
        String fingerprint = Build.FINGERPRINT;
        if (fingerprint != null) {
            result.append(fingerprint);
        }
        String serial = getDeviceSerialNumber();
        if (serial != null) {
            result.append(serial);
        }
        try {
            return result.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported");
        }
    }

    private static String getDeviceSerialNumber() {
        // We're using the Reflection API because Build.SERIAL is only available
        // since API Level 9 (Gingerbread, Android 2.3).
        try {
            return (String) Build.class.getField("SERIAL").get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] generateSeed() {
        try {
            ByteArrayOutputStream seedBuffer = new ByteArrayOutputStream();
            DataOutputStream seedBufferOut =
                    new DataOutputStream(seedBuffer);
            seedBufferOut.writeLong(System.currentTimeMillis());
            seedBufferOut.writeLong(System.nanoTime());
            seedBufferOut.writeInt(Process.myPid());
            seedBufferOut.writeInt(Process.myUid());
            seedBufferOut.write(BUILD_FINGERPRINT_AND_DEVICE_SERIAL);
            seedBufferOut.close();
            return seedBuffer.toByteArray();
        } catch (IOException e) {
            throw new SecurityException("Failed to generate seed", e);
        }
    }

    public static private_key from_seed(String strSeed) {
        sha256_object.encoder encoder = new sha256_object.encoder();

        encoder.write(strSeed.getBytes(Charset.forName("UTF-8")));

        return new private_key(encoder.result().hash);
    }

    public sha512_object get_shared_secret(public_key publicKey) {
        ECKey ecPublicKey = ECKey.fromPublicOnly(publicKey.getKeyByte());
        ECKey ecPrivateKey = ECKey.fromPrivate(key_data);

        byte[] secret = ecPublicKey.getPubKeyPoint().multiply(ecPrivateKey.getPrivKey())
                .normalize().getXCoord().getEncoded();

        return sha512_object.create_from_byte_array(secret, 0, secret.length);
    }

}
