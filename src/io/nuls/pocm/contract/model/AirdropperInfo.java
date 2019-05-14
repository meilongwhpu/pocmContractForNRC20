package io.nuls.pocm.contract.model;

import java.math.BigInteger;

/**
 * Airdrop information
 *
 * @author: Long
 * @date: 2019-03-15
 */
public class AirdropperInfo {

    /**
     * Receiving Airdrop Address
     */
    private String receiverAddress;

    /**
     * Quantity of airdrop
     */
    private BigInteger airdropperAmount;

    public AirdropperInfo() {
        this.airdropperAmount = BigInteger.ZERO;
    }

    public AirdropperInfo(AirdropperInfo info) {
        this.receiverAddress = info.receiverAddress;
        this.airdropperAmount = info.airdropperAmount;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public BigInteger getAirdropperAmount() {
        return airdropperAmount;
    }

    public void setAirdropperAmount(BigInteger airdropperAmount) {
        this.airdropperAmount = airdropperAmount;
    }

    @Override
    public String toString() {
        return "{receiverAddress:" + receiverAddress + ",airdropperAmount:" + airdropperAmount + "}";
    }
}
