package jrippleapi.keys;

import java.io.File;
import java.math.BigInteger;

import jrippleapi.TestUtilities;
import jrippleapi.core.RippleAddress;

import org.junit.Test;

public class RippleWalletTest {
	@Test
	public void testSendXRP() throws Exception {
		File testWalletFile = new File("testdata/jrippleAPI.wallet");
		RippleWallet wallet = RippleWallet.createWallet(TestUtilities.getTestSeed(), testWalletFile);
		wallet.sendXRP(BigInteger.ONE, RippleAddress.RIPPLE_ADDRESS_PMARCHES);
		testWalletFile.delete();
	}
}
