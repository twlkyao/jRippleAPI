package jrippleapi.serialization;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import org.bouncycastle.jcajce.provider.symmetric.DES;

import jrippleapi.core.DenominatedIssuedCurrency;
import jrippleapi.core.RippleAddress;
import jrippleapi.core.RipplePath;
import jrippleapi.core.RipplePathElement;
import jrippleapi.core.RipplePathSet;
import jrippleapi.core.RipplePrivateKey;
import jrippleapi.serialization.RippleBinarySchema.BinaryFormatField;
import jrippleapi.serialization.RippleBinarySchema.PrimitiveTypes;

public class RippleBinarySerializer {
	protected static final long MIN_VALUE = 1000000000000000l;
	protected static final long MAX_VALUE = 9999999999999999l;

	public RippleBinaryObject readBinaryObject(ByteBuffer input) {
		RippleBinaryObject serializedObject = new RippleBinaryObject();
		while(input.hasRemaining()){
			byte firstByte = input.get();
			int type=(0xF0 & firstByte)>>4;
			if(type==0){
				type = input.get();
			}
			int field=0x0F & firstByte;
			if(field==0){
				field = input.get();
				firstByte=(byte)field;
			}

			BinaryFormatField serializedField = BinaryFormatField.lookup(type, field);
			Object value = readPrimitive(input, serializedField.primitive);
			serializedObject.fields.put(serializedField, value );
		}
		return serializedObject;
	}

	protected Object readPrimitive(ByteBuffer input, PrimitiveTypes primitive) {
		if(primitive==PrimitiveTypes.UINT16){
			return 0xFFFFFFFF & input.getShort();
		}
		else if(primitive==PrimitiveTypes.UINT32){
			return 0xFFFFFFFFFFFFFFFFl & input.getInt();
		}
		else if(primitive==PrimitiveTypes.UINT64){
			byte[] eightBytes = new byte[8];
			input.get(eightBytes);
			return new BigInteger(1, eightBytes);
		}
		else if(primitive==PrimitiveTypes.HASH128){
			byte[] sixteenBytes = new byte[16];
			input.get(sixteenBytes);
			return sixteenBytes;
		}
		else if(primitive==PrimitiveTypes.HASH256){
			byte[] thirtyTwoBytes = new byte[32];
			input.get(thirtyTwoBytes);
			return thirtyTwoBytes;
		}
		else if(primitive==PrimitiveTypes.AMOUNT){
			return readAmount(input);
		}
		else if(primitive==PrimitiveTypes.VARIABLE_LENGTH){
			return readVariableLength(input);
		}
		else if(primitive==PrimitiveTypes.ACCOUNT){
			return readAccount(input);
		}
		else if(primitive==PrimitiveTypes.OBJECT){
			throw new RuntimeException("Object type, not yet supported");
		}
		else if(primitive==PrimitiveTypes.ARRAY){
			throw new RuntimeException("Array type, not yet supported");
		}
		else if(primitive==PrimitiveTypes.UINT8){
			return 0xFFFF & input.get();
		}
		else if(primitive==PrimitiveTypes.HASH160){
			return readIssuer(input);
		}
		else if(primitive==PrimitiveTypes.PATHSET){
			return readPathSet(input);
		}
		else if(primitive==PrimitiveTypes.VECTOR256){
			throw new RuntimeException("Vector");
		}
		throw new RuntimeException("Unsupported primitive "+primitive);
	}

	protected RippleAddress readAccount(ByteBuffer input) {
		byte[] accountBytes = readVariableLength(input);
		return new RippleAddress(accountBytes);
	}

	//See https://ripple.com/wiki/Currency_Format
	protected DenominatedIssuedCurrency readAmount(ByteBuffer input) {
		long offsetNativeSignMagnitudeBytes = input.getLong();
		//1 bit for Native
		boolean isXRPAmount =(0x8000000000000000l & offsetNativeSignMagnitudeBytes)==0; 
		//1 bit for sign
		int sign = (0x4000000000000000l & offsetNativeSignMagnitudeBytes)==0?-1:1;
		//8 bits of offset
		int offset = (int) ((offsetNativeSignMagnitudeBytes & 0x3FC0000000000000l)>>>54);
		//The remaining 54 bits are magnitude
		long longMagnitude = offsetNativeSignMagnitudeBytes&0x3FFFFFFFFFFFFFl;
		if(isXRPAmount){
			BigDecimal magnitude = BigDecimal.valueOf(sign*longMagnitude);
			return new DenominatedIssuedCurrency(magnitude);
		}
		else{
			String currencyStr = readCurrency(input);
			RippleAddress issuer = readIssuer(input);
			if(offset==0 || longMagnitude==0){
				return new DenominatedIssuedCurrency(BigDecimal.ZERO, issuer, currencyStr);
			}

			int decimalPosition = 97-offset;
			if(decimalPosition<DenominatedIssuedCurrency.MIN_SCALE || decimalPosition>DenominatedIssuedCurrency.MAX_SCALE){
				throw new RuntimeException("invalid scale "+decimalPosition);
			}
			BigInteger biMagnitude = BigInteger.valueOf(longMagnitude);
			BigDecimal fractionalValue=new BigDecimal(biMagnitude, decimalPosition);
			return new DenominatedIssuedCurrency(fractionalValue, issuer, currencyStr);
		}
	}

	protected RippleAddress readIssuer(ByteBuffer input) {
		byte[] issuerBytes = new byte[20];
		input.get(issuerBytes);
		//TODO If issuer is all 0, this means any issuer
		return new RippleAddress(issuerBytes);
	}

	protected String readCurrency(ByteBuffer input) {
		byte[] unknown = new byte[12];
		input.get(unknown);
		byte[] currency = new byte[8];
		input.get(currency);
		return new String(currency, 0, 3);
		//TODO See https://ripple.com/wiki/Currency_Format for format
	}

	protected byte[] readVariableLength(ByteBuffer input) {
		int byteLen=0;
		int firstByte = input.get();
		int secondByte=0;
		if(firstByte<192){
			byteLen=firstByte;
		}
		else if(firstByte<240){
			secondByte = input.get();
			byteLen=193+(firstByte-193)*256 + secondByte;
		}
		else if(firstByte<254){
			secondByte = input.get();
			int thirdByte = input.get();
			byteLen=12481 + (firstByte-241)*65536 + secondByte*256 + thirdByte;
		}
		else {
			throw new RuntimeException("firstByte="+firstByte+", value reserved");
		}

		byte[] variableBytes = new byte[byteLen];
		input.get(variableBytes);
		return variableBytes;
	}

	protected RipplePathSet readPathSet(ByteBuffer input) {
		RipplePathSet pathSet = new RipplePathSet();
		RipplePath path = null;
		while(true){
			byte pathElementType = input.get();
			if(pathElementType==(byte)0x00){ //End of Path set
				break;
			}
			if(path==null){
				path = new RipplePath();
				pathSet.add(path);
			}
			if(pathElementType==(byte)0xFF){ //End of Path
				path = null;
				continue;
			}

			RipplePathElement pathElement = new RipplePathElement();
			path.add(pathElement);
			if((pathElementType&0x01)!=0){ //Account bit is set
				pathElement.account = readIssuer(input);
			}
			if((pathElementType&0x10)!=0){ //Currency bit is set
				pathElement.currency = readCurrency(input);
			}
			if((pathElementType&0x20)!=0){ //Issuer bit is set
				pathElement.issuer = readIssuer(input);
			}
		}
		
		return pathSet;
	}

	public ByteBuffer writeBinaryObject(RippleBinaryObject serializedObj) {
		ByteBuffer output = ByteBuffer.allocate(2000); //FIXME Hum.. CReate GRowable ByteBuffer class or use ByteArrayOutputStream?
		List<BinaryFormatField> sortedFields = serializedObj.getSortedField();
		for(BinaryFormatField field: sortedFields){
			byte typeHalfByte=0;
			if(field.primitive.typeCode<=15){
				typeHalfByte = (byte) (field.primitive.typeCode<<4);
			}
			byte fieldHalfByte = 0;
			if(field.fieldId<=15){
				fieldHalfByte = (byte) (field.fieldId&0x0F);
			}
			output.put((byte) (typeHalfByte|fieldHalfByte));
			if(typeHalfByte==0){
				output.put((byte) field.primitive.typeCode);
			}
			if(fieldHalfByte==0){
				output.put((byte) field.fieldId);
			}
			
			writePrimitive(output, field.primitive, serializedObj.getField(field));
		}
		output.flip();
		ByteBuffer compactBuffer = ByteBuffer.allocate(output.limit());
		compactBuffer.put(output);
		compactBuffer.flip();
		return compactBuffer;
	}

	protected void writePrimitive(ByteBuffer output, PrimitiveTypes primitive, Object value) {
		if(primitive==PrimitiveTypes.UINT16){
			int intValue = (int) value;
			if(intValue>0xFFFF){
				throw new RuntimeException("UINT16 overflow for value "+value);
			}
			output.put((byte) (intValue>>8&0xFF));
			output.put((byte) (intValue&0xFF));
		}
		else if(primitive==PrimitiveTypes.UINT32){
			long longValue = (long) value;
			if(longValue>0xFFFFFFFFl){
				throw new RuntimeException("UINT32 overflow for value "+value);
			}
			output.put((byte) (longValue>>24&0xFF));
			output.put((byte) (longValue>>16&0xFF));
			output.put((byte) (longValue>>8&0xFF));
			output.put((byte) (longValue&0xFF));
		}
		else if(primitive==PrimitiveTypes.UINT64){
			byte[] biBytes = RipplePrivateKey.bigIntegerToBytes((BigInteger) value, 8);
			if(biBytes.length!=8){
				throw new RuntimeException("UINT64 overflow for value "+value);
			}
			output.put(biBytes);
		}
		else if(primitive==PrimitiveTypes.HASH128){
			byte[] sixteenBytes = (byte[]) value;
			if(sixteenBytes.length!=16){
				throw new RuntimeException("value "+value+" is not a HASH128");
			}
			output.put(sixteenBytes);
		}
		else if(primitive==PrimitiveTypes.HASH256){
			byte[] thirtyTwoBytes = (byte[]) value;
			if(thirtyTwoBytes.length!=32){
				throw new RuntimeException("value "+value+" is not a HASH256");
			}
			output.put(thirtyTwoBytes);
		}
		else if(primitive==PrimitiveTypes.AMOUNT){
			writeAmount(output, (DenominatedIssuedCurrency) value);
		}
		else if(primitive==PrimitiveTypes.VARIABLE_LENGTH){
			writeVariableLength(output, (byte[]) value);
		}
		else if(primitive==PrimitiveTypes.ACCOUNT){
			writeAccount(output, (RippleAddress) value);
		}
		else if(primitive==PrimitiveTypes.OBJECT){
			throw new RuntimeException("Object type, not yet supported");
		}
		else if(primitive==PrimitiveTypes.ARRAY){
			throw new RuntimeException("Array type, not yet supported");
		}
		else if(primitive==PrimitiveTypes.UINT8){
			int intValue = (int) value;
			if(intValue>0xFF){
				throw new RuntimeException("UINT8 overflow for value "+value);
			}
			output.put((byte) value);
		}
		else if(primitive==PrimitiveTypes.HASH160){
			writeIssuer(output, (RippleAddress) value);
		}
		else if(primitive==PrimitiveTypes.PATHSET){
			writePathSet(output, (RipplePathSet) value);
		}
		else if(primitive==PrimitiveTypes.VECTOR256){
			throw new RuntimeException("Vector");
		}
		else{
			throw new RuntimeException("Unsupported primitive "+primitive);
		}
	}

	protected void writePathSet(ByteBuffer output, RipplePathSet pathSet) {
		loopPathSet:
		for(int i=0; i<pathSet.size(); i++){
			RipplePath path=pathSet.get(i);
			for(int j=0; j<path.size(); j++){
				RipplePathElement pathElement=path.get(j);
				byte pathElementType=0;
				if(pathElement.account!=null){
					pathElementType|=0x01;
				}
				if(pathElement.currency!=null){
					pathElementType|=0x10;
				}
				if(pathElement.issuer!=null){ //Issuer bit is set
					pathElementType|=0x20;
				}
				output.put(pathElementType);
				
				if(pathElement.account!=null){ //Account bit is set
					writeIssuer(output, pathElement.account);
				}
				if(pathElement.currency!=null){
					writeCurrency(output, pathElement.currency);
				}
				if(pathElement.issuer!=null){ //Issuer bit is set
					writeIssuer(output, pathElement.issuer);
				}
				if(i+1==pathSet.size() && j+1==path.size()){
					break loopPathSet;
				}
			}
			
			output.put((byte) 0xFF); //End of path
		}
		output.put((byte) 0); //End of path set
	}

	protected void writeIssuer(ByteBuffer output, RippleAddress value) {
		byte[] issuerBytes = value.getBytes();
		output.put(issuerBytes);
	}

	protected void writeAccount(ByteBuffer output, RippleAddress address) {
		writeVariableLength(output, address.getBytes());
	}

	//TODO Unit test this function
	protected void writeVariableLength(ByteBuffer output, byte[] value) {
		if(value.length<192){
			output.put((byte) value.length);
		}
		else if(value.length<12480){ //193 + (b1-193)*256 + b2
			//FIXME This is not right...
			int firstByte=(value.length/256)+193;
			output.put((byte) firstByte);
			//FIXME What about arrays of length 193?
			int secondByte=value.length-firstByte-193;
			output.put((byte) secondByte);
		}
		else if(value.length<918744){ //12481 + (b1-241)*65536 + b2*256 + b3
			int firstByte=(value.length/65536)+241;
			output.put((byte) firstByte);
			int secondByte=(value.length-firstByte)/256;
			output.put((byte) secondByte);
			int thirdByte=value.length-firstByte-secondByte-12481;
			output.put((byte) thirdByte);
		}
		output.put(value);
	}

	protected void writeAmount(ByteBuffer output, DenominatedIssuedCurrency denominatedCurrency) {
//		System.out.println(DatatypeConverter.printHexBinary(output.array()));
		long offsetNativeSignMagnitudeBytes=0;
		if(denominatedCurrency.amount.signum()>0){
			offsetNativeSignMagnitudeBytes|= 0x4000000000000000l;
		}
		if(denominatedCurrency.currency==null){
			long drops = denominatedCurrency.amount.longValue(); //XRP does not have fractional portion
			offsetNativeSignMagnitudeBytes|=drops;
			output.putLong(offsetNativeSignMagnitudeBytes);
		}
		else{
			offsetNativeSignMagnitudeBytes|= 0x8000000000000000l;
			BigInteger unscaledValue = denominatedCurrency.amount.unscaledValue();
			if(unscaledValue.longValue()!=0){
				int scale = denominatedCurrency.amount.scale();
				long offset = 97-scale;
				offsetNativeSignMagnitudeBytes|=(offset<<54);
//				if(unscaledValue.longValue()<MIN_VALUE || unscaledValue.longValue()>MAX_VALUE){
//					throw new RuntimeException("value "+unscaledValue+" is out of range");
//				}
				offsetNativeSignMagnitudeBytes|=unscaledValue.abs().longValue();
			}
			output.putLong(offsetNativeSignMagnitudeBytes);
			writeCurrency(output, denominatedCurrency.currency);
			writeIssuer(output, denominatedCurrency.issuer);
		}
	}

	protected void writeCurrency(ByteBuffer output, String currency) {
		byte[] currencyBytes = new byte[20];
		System.arraycopy(currency.getBytes(), 0, currencyBytes, 12, 3);
		output.put(currencyBytes);
		//TODO See https://ripple.com/wiki/Currency_Format for format
	}

}
