package jrippleapi.core;

import java.math.BigDecimal;

import jrippleapi.connection.JSONSerializable;

import org.json.simple.JSONObject;

public class DenominatedIssuedCurrency implements JSONSerializable {
	public BigDecimal amount;
	public RippleAddress issuer;
	public String currency;
	public static final int MIN_SCALE = -96;
	public static final int MAX_SCALE = 80;

	public DenominatedIssuedCurrency(){ //FIXME get rid of this
	}

	public DenominatedIssuedCurrency(String amountStr, RippleAddress issuer, String currencyStr){
		this(new BigDecimal(amountStr).stripTrailingZeros(), issuer, currencyStr);
	}
	
	public DenominatedIssuedCurrency(BigDecimal amount, RippleAddress issuer, String currencyStr){
		int oldScale=amount.scale();
		if(oldScale<MIN_SCALE || oldScale>MAX_SCALE){
			int newScale=MAX_SCALE-(amount.precision()-amount.scale());
			if(newScale<MIN_SCALE || newScale>MAX_SCALE){
				throw new RuntimeException("newScale "+newScale+" is out of range");
			}
			amount=amount.setScale(newScale);
		}
		this.amount = amount;
		this.issuer = issuer;
		this.currency = currencyStr;
	}
	
	public DenominatedIssuedCurrency(BigDecimal xrpAmount) {
		this.amount=xrpAmount;
	}
	
	public DenominatedIssuedCurrency(int xrpAmount) {
		this(BigDecimal.valueOf(xrpAmount));
	}

	public boolean isNative() {
		return issuer==null;
	}

	public boolean isNegative() {
		return amount.signum()==-1;
	}

	@Override
	public String toString() {
		if(issuer==null || currency==null){
			return amount.movePointLeft(6).stripTrailingZeros().toPlainString()+" XRP";
		}
		return amount.stripTrailingZeros().toPlainString()+"/"+currency+"/"+issuer;
	}

	@Override
	public void copyFrom(JSONObject jsonDenomination) {
		issuer = new RippleAddress(((String) jsonDenomination.get("issuer")));
		String currencyStr = ((String) jsonDenomination.get("currency"));
		currency = currencyStr;

		String amountStr = (String) jsonDenomination.get("value");
		amount=new BigDecimal(amountStr);
	}

	public void copyFrom(Object jsonObject) {
		if(jsonObject instanceof String){
			amount=new BigDecimal((String) jsonObject);
		}
		else{
			copyFrom((JSONObject) jsonObject);
		}
	}

	public Object toJSON(){
		if(currency==null){
			return amount.toString();
		}
		else{
			JSONObject jsonThis = new JSONObject();
			jsonThis.put("value", amount.toString());
			jsonThis.put("issuer", issuer.toString());
			jsonThis.put("currency", currency);
			return jsonThis;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((amount == null) ? 0 : amount.hashCode());
		result = prime * result
				+ ((currency == null) ? 0 : currency.hashCode());
		result = prime * result
				+ ((issuer == null) ? 0 : issuer.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DenominatedIssuedCurrency other = (DenominatedIssuedCurrency) obj;
		if (amount == null) {
			if (other.amount != null)
				return false;
		} else if (amount.compareTo(other.amount)!=0)
			return false;
		if (currency == null) {
			if (other.currency != null)
				return false;
		} else if (!currency.equals(other.currency))
			return false;
		if (issuer == null) {
			if (other.issuer != null)
				return false;
		} else if (!issuer.equals(other.issuer))
			return false;
		return true;
	}
	
}
