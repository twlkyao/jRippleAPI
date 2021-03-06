package jrippleapi.serialization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import javax.xml.bind.DatatypeConverter;

import jrippleapi.core.DenominatedIssuedCurrency;
import jrippleapi.core.RippleAddress;
import jrippleapi.core.RipplePaymentTransaction;
import jrippleapi.serialization.RippleBinarySchema.BinaryFormatField;
import jrippleapi.serialization.RippleBinarySchema.TransactionTypes;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class RippleBinarySerializerTest {

	@Test
	public void testReadPayment() throws IOException {
		MappedByteBuffer payment1ByteBuffer = fileToBuffer("testdata/r32fLio1qkmYqFFYkwdnsaVN7cxBwkW4cT-to-rEQQNvhuLt1KTYmDWmw12mPvmJD4KCtxmS-amt-1000000XRP.bin");
		
		RippleBinarySerializer binSer = new RippleBinarySerializer();
		RippleBinaryObject serObj = binSer.readBinaryObject(payment1ByteBuffer);
		assertEquals(TransactionTypes.PAYMENT, serObj.getTransactionType());
		assertEquals("r32fLio1qkmYqFFYkwdnsaVN7cxBwkW4cT", serObj.getField(BinaryFormatField.Account).toString());
		assertEquals("rEQQNvhuLt1KTYmDWmw12mPvmJD4KCtxmS", serObj.getField(BinaryFormatField.Destination).toString());
		assertEquals("1 XRP", serObj.getField(BinaryFormatField.Amount).toString());
		ByteBuffer readBuffer = binSer.writeBinaryObject(serObj);
		payment1ByteBuffer.rewind();
		assertEquals(payment1ByteBuffer, readBuffer);
	}

	@Test
	public void testTrustSet() throws IOException {
		MappedByteBuffer trustet1ByteBuffer = fileToBuffer("testdata/trustset1.bin");
		
		RippleBinarySerializer binSer = new RippleBinarySerializer();
		RippleBinaryObject serObj = binSer.readBinaryObject(trustet1ByteBuffer);
		assertEquals(TransactionTypes.TRUST_SET, serObj.getTransactionType());
		assertEquals(RippleAddress.RIPPLE_ADDRESS_JRIPPLEAPI, serObj.getField(BinaryFormatField.Account));

		final DenominatedIssuedCurrency EXPECTED_FEE = new DenominatedIssuedCurrency(BigDecimal.TEN);
		assertEquals(EXPECTED_FEE, serObj.getField(BinaryFormatField.Fee));

		final DenominatedIssuedCurrency EXPECTED_TRUST_AMOUNT = new DenominatedIssuedCurrency(BigDecimal.valueOf(1), RippleAddress.RIPPLE_ADDRESS_PMARCHES, "BTC");
		assertEquals(EXPECTED_TRUST_AMOUNT, serObj.getField(BinaryFormatField.LimitAmount));

		assertNotNull(serObj.getField(BinaryFormatField.SigningPubKey));
		assertNotNull(serObj.getField(BinaryFormatField.TxnSignature));
	}

	private MappedByteBuffer fileToBuffer(String filename) throws IOException {
		FileSystem fs = FileSystems.getDefault();
		Path trustSet1Path = fs.getPath(filename);
		FileChannel trust1FC = FileChannel.open(trustSet1Path, StandardOpenOption.READ);
		MappedByteBuffer trust1ByteBuffer = trust1FC.map(MapMode.READ_ONLY, 0, trust1FC.size());
		trust1FC.close();
		return trust1ByteBuffer;
	}

	@Test
	public void testManyFiles() throws IOException{
		RippleBinarySerializer binSer = new RippleBinarySerializer();
		LineNumberReader reader = new LineNumberReader(new FileReader("testdata/12kRawTxn.hex"));
		while(true)
		{
			String line = reader.readLine();
			if(line==null){
				break;
			}
			byte[] txBytes = DatatypeConverter.parseHexBinary(line);
			ByteBuffer buffer = ByteBuffer.wrap(txBytes);
			RippleBinaryObject serObj = binSer.readBinaryObject(buffer);
			ByteBuffer readBuffer = binSer.writeBinaryObject(serObj);
			assertEquals(line, DatatypeConverter.printHexBinary(readBuffer.array()));
		}
		reader.close();
	}
	
	@Test
	public void testSerializedTx() throws Exception {
		RippleBinarySerializer binSer = new RippleBinarySerializer();
		JSONArray allTx = (JSONArray) new JSONParser().parse(new FileReader("testdata/unittest-tx.json"));
		for(Object obj : allTx){
			JSONObject tx = (JSONObject) obj;
			String hexTx = (String) tx.get("tx");
			byte[] txBytes = DatatypeConverter.parseHexBinary(hexTx);
			ByteBuffer buffer = ByteBuffer.wrap(txBytes);
			RippleBinaryObject txRead = binSer.readBinaryObject(buffer);
			RipplePaymentTransaction payment = new RipplePaymentTransaction(txRead);
			assertEquals(tx.get("payee"), txRead.getField(BinaryFormatField.Destination).toString());
			assertEquals(tx.get("payer"), txRead.getField(BinaryFormatField.Account).toString());
			assertEquals(tx.get("amount"), txRead.getField(BinaryFormatField.Amount).toString());
			assertEquals(tx.get("inLedger").toString(), tx.get("fee"), txRead.getField(BinaryFormatField.Fee).toString());
			ByteBuffer writtenBytes = binSer.writeBinaryObject(txRead);
			assertEquals(hexTx, DatatypeConverter.printHexBinary(writtenBytes.array()));
		}
	}
	
	@Test
	public void testWriteAndReadPaymentTransaction(){
		RippleBinarySerializer binSer = new RippleBinarySerializer();
		DenominatedIssuedCurrency amount = new DenominatedIssuedCurrency(BigDecimal.valueOf(1));
		RipplePaymentTransaction payment = new RipplePaymentTransaction(RippleAddress.RIPPLE_ADDRESS_JRIPPLEAPI, RippleAddress.RIPPLE_ADDRESS_PMARCHES, amount, 1);
		ByteBuffer byteBuffer = binSer.writeBinaryObject(payment.getBinaryObject());
		RippleBinaryObject serObjRead = binSer.readBinaryObject(byteBuffer);
//		assertEquals(payment, new RipplePaymentTransaction(serObjRead));
		ByteBuffer writtenObj = binSer.writeBinaryObject(serObjRead);
		byteBuffer.rewind();
		assertEquals(byteBuffer, writtenObj);
	}
	
	@Test
	public void testNegativeAmounts(){
        DenominatedIssuedCurrency amount = new DenominatedIssuedCurrency("-99.2643419677474", RippleAddress.RIPPLE_ADDRESS_NEUTRAL, "USD");

        assertEquals(13, amount.amount.scale());
        assertTrue(amount.isNegative());
        assertFalse(amount.isNative());
        assertEquals("-99.2643419677474/USD/rrrrrrrrrrrrrrrrrrrrBZbvji", amount.toString());
        
		ByteBuffer output=ByteBuffer.allocate(48);
		new RippleBinarySerializer().writeAmount(output, amount);
		String hex = DatatypeConverter.printHexBinary(output.array());
        String scale14expectedHex = "94E3440A102F5F5400000000000000000000000055534400000000000000000000000000000000000000000000000001";
        String scale13ExpectedHex="950386CDCE6B232200000000000000000000000055534400000000000000000000000000000000000000000000000001";
        assertEquals(scale13ExpectedHex, hex);
	}
}
