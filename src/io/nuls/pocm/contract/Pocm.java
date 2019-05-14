/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2018 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.pocm.contract;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Block;
import io.nuls.contract.sdk.Contract;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.annotation.Payable;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;
import io.nuls.pocm.contract.event.DepositInfoEvent;
import io.nuls.pocm.contract.event.MiningInfoEvent;
import io.nuls.pocm.contract.model.*;
import io.nuls.pocm.contract.service.AriDropperService;
import io.nuls.pocm.contract.service.DepositService;
import io.nuls.pocm.contract.service.MiningService;
import io.nuls.pocm.contract.token.PocmToken;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static io.nuls.contract.sdk.Utils.emit;
import static io.nuls.contract.sdk.Utils.require;
import static io.nuls.pocm.contract.util.PocmUtil.*;

/**
 * @author: Long
 * @date: 2019-03-15
 */
public class Pocm extends PocmToken implements Contract {

    private long NUMBER = 1L;

    private DepositService depositService;

    private MiningService miningService;

    private AriDropperService ariDropperService;

    public Pocm(@Required String name, @Required String symbol, @Required BigInteger initialAmount, @Required int decimals, @Required BigDecimal price, @Required int awardingCycle,
                @Required BigDecimal minimumDepositNULS, @Required int minimumLocked, String rewardHalvingCycle, String maximumDepositAddressCount, String[] receiverAddress, long[] receiverAmount) {
        super(name, symbol, initialAmount, decimals, receiverAddress, receiverAmount);

        // Check that the decimal number of price must not be greater than decimals
        require(price.compareTo(BigDecimal.ZERO) > 0, "Price should be greater than 0");
        require(checkMaximumDecimals(price, decimals), "Maximum " + decimals + "-bit decimal");
        require(minimumLocked > 0, "The minimum lock block value should be greater than 0");
        require(awardingCycle > 0, "Incentive distribution cycle should be greater than 0");
        int rewardHalvingCycleForInt = 0;
        int maximumDepositAddressCountForInt = 0;
        if (rewardHalvingCycle != null && rewardHalvingCycle.trim().length() > 0) {
            require(canConvertNumeric(rewardHalvingCycle.trim(), String.valueOf(Integer.MAX_VALUE)), "The half-cycle input of the reward is illegal, and the number character less than 2147483647 should be input.");
            rewardHalvingCycleForInt = Integer.parseInt(rewardHalvingCycle.trim());
            require(rewardHalvingCycleForInt >= 0, "The half-life of the reward should be greater than or equal to 0.");
        }
        if (maximumDepositAddressCount != null && maximumDepositAddressCount.trim().length() > 0) {
            require(canConvertNumeric(maximumDepositAddressCount.trim(), String.valueOf(Integer.MAX_VALUE)), "The minimum amount of mortgage is illegally entered, and digital characters less than 2147483647 should be entered.");
            maximumDepositAddressCountForInt = Integer.parseInt(maximumDepositAddressCount.trim());
            require(maximumDepositAddressCountForInt >= 0, "The minimum amount of mortgage should be greater than or equal to 0.");
        }

        depositService = new DepositService(minimumLocked, toNa(minimumDepositNULS), maximumDepositAddressCountForInt);
        miningService = new MiningService(Block.number(), awardingCycle, rewardHalvingCycleForInt, decimals, price);
        ariDropperService = new AriDropperService();

        BigInteger receiverTotalAmount = BigInteger.ZERO;
        if (receiverAddress != null && receiverAmount != null) {
            Address[] receiverAddr = convertStringToAddres(receiverAddress);
            //Airdrop Token to the Receiver Address
            for (int i = 0; i < receiverAddress.length; i++) {
                if (receiverAddress[i].equals(Msg.sender().toString())) {
                    continue;
                }
                AirdropperInfo info = new AirdropperInfo();
                info.setReceiverAddress(receiverAddress[i]);
                BigInteger receiverSupply = BigInteger.valueOf(receiverAmount[i]).multiply(BigInteger.TEN.pow(decimals));
                info.setAirdropperAmount(receiverSupply);
                ariDropperService.addAriDropperInfo(info);
                addBalance(receiverAddr[i], receiverSupply);
                emit(new TransferEvent(null, receiverAddr[i], receiverSupply));
                receiverTotalAmount = receiverTotalAmount.add(BigInteger.valueOf(receiverAmount[i]));
            }
        }

        BigInteger canInitialAmount = initialAmount.subtract(receiverTotalAmount);
        BigInteger initialCreaterSupply = canInitialAmount.multiply(BigInteger.TEN.pow(decimals));
        addBalance(Msg.sender(), initialCreaterSupply);
        emit(new TransferEvent(null, Msg.sender(), initialCreaterSupply));

        if (initialAmount.compareTo(receiverTotalAmount) >= 0) {
            AirdropperInfo info = new AirdropperInfo();
            info.setReceiverAddress(Msg.sender().toString());
            info.setAirdropperAmount(initialAmount.subtract(receiverTotalAmount).multiply(BigInteger.TEN.pow(decimals)));
            ariDropperService.addAriDropperInfo(info);
        }
    }

    /**
     * Get Token by Mortgaging Nuls for Yourself
     *
     * @return
     */
    @Payable
    public void depositForOwn() {
        BigInteger value = Msg.value();
        String userStr = Msg.sender().toString();
        long currentHeight = Block.number();
        long depositNumber = NUMBER++;
        DepositInfo info = depositService.addDeposit(userStr, userStr, value, currentHeight, depositNumber);

        //Add the number of mortgages to the queue
        miningService.putDeposit(value, currentHeight);

        //Initialization of mining information
        miningService.initMingInfo(currentHeight, userStr, userStr, depositNumber);

        emit(new DepositInfoEvent(info));
    }

    /**
     * Get Token by Mortgaging Nuls for Others
     *
     * @param miningAddress Specify an acceptable address to get Token
     * @return
     */
    @Payable
    public void depositForOther(@Required Address miningAddress) {
        String userStr = Msg.sender().toString();
        BigInteger value = Msg.value();
        long currentHeight = Block.number();
        long depositNumber = NUMBER++;
        DepositInfo info = depositService.addDeposit(userStr, miningAddress.toString(), value, currentHeight, depositNumber);

        //Add the number of mortgages to the queue
        miningService.putDeposit(value, currentHeight);

        //Initialization of mining information
        miningService.initMingInfo(currentHeight, miningAddress.toString(), userStr, depositNumber);

        emit(new DepositInfoEvent(info));
    }

    /**
     * Withdrawal from mortgage mining and withdrawal from all mortgages when the mortgage number is 0
     *
     * @param number Mortgage number
     * @return
     */
    public void quit(String number) {
        long currentHeight = Block.number();
        String userStr = Msg.sender().toString();
        long depositNumber = 0;
        if (number != null && number.trim().length() > 0) {
            require(canConvertNumeric(number.trim(), String.valueOf(Long.MAX_VALUE)), "Mortgage Number Input is Illegal and Digital Characters should be Input");
            depositNumber = Long.valueOf(number.trim());
        }
        DepositInfo depositInfo = depositService.getDepositInfo(userStr);
        require(depositInfo != null, "This user is not involved in the mortgage");

        // Award
        this.receive(depositInfo);

        BigInteger deposit;
        MiningInfo miningInfo;

        //Withdrawal of all mortgages
        if (depositNumber == 0) {
            miningInfo = miningService.getMiningInfo(depositInfo.getDepositorAddress());
            long result = depositService.checkAllDepositLocked(depositInfo);
            require(result == -1, "The mortgaged NULS is not fully unlocked");

            deposit = depositInfo.getDepositTotalAmount();
            miningService.removeAllMiningInfo(depositInfo);

            Map<Long, DepositDetailInfo> depositDetailInfos = depositInfo.getDepositDetailInfos();

            //Withdrawal from the totalDepositList queue
            for (Long key : depositDetailInfos.keySet()) {
                DepositDetailInfo detailInfo = depositDetailInfos.get(key);
                miningService.quitDeposit(detailInfo, currentHeight);
            }

            depositService.clearDepositDetailInfos(depositInfo);
        } else {
            //Withdrawal from a mortgage
            DepositDetailInfo detailInfo = depositInfo.getDepositDetailInfoByNumber(depositNumber);

            miningInfo = miningService.getMiningInfo(detailInfo.getMiningAddress());

            long unLockedHeight = depositService.checkDepositLocked(detailInfo);
            require(unLockedHeight == -1, "In mining locking, the unlocking height is " + unLockedHeight);

            //Delete mining information
            miningService.removeMiningInfo(detailInfo.getMiningAddress(), depositNumber);

            //Delete Mortgage Information
            depositInfo.removeDepositDetailInfoByNumber(depositNumber);

            // Return the deposit money
            deposit = detailInfo.getDepositAmount();
            depositInfo.setDepositTotalAmount(depositInfo.getDepositTotalAmount().subtract(deposit));
            depositInfo.setDepositCount(depositInfo.getDepositCount() - 1);

            //Withdrawal from the totalDepositList queue
            miningService.quitDeposit(detailInfo, currentHeight);
        }

        depositService.setTotalDeposit(depositService.getTotalDeposit().subtract(deposit));

        if (depositInfo.getDepositDetailInfos().size() == 0) {
            depositService.removeDeposit(userStr);
        }
        Msg.sender().transfer(deposit);

        emit(new MiningInfoEvent(miningInfo));
    }

    /**
     * Receive Token for the mortgage nuls
     */
    public void receiveAwards() {
        Address user = Msg.sender();
        MiningInfo miningInfo = miningService.getMiningInfo(user.toString());
        require(miningInfo != null, "Mining information that does not mortgage itself");
        DepositInfo depositInfo = depositService.getDepositInfo(user.toString());
        require(depositInfo != null, "This user is not involved in the mortgage");
        this.receive(depositInfo);
        emit(new MiningInfoEvent(miningInfo));
    }

    /**
     * The Token Award is initiated by the receiving address;
     * when the mortgage user makes a mortgage for other users to dig, the receiving token user can initiate this method.
     *
     * @return
     */
    public void receiveAwardsForMiningAddress() {
        List<String> alreadyReceive = new ArrayList<String>();
        Address user = Msg.sender();
        MiningInfo info = miningService.getMiningInfo(user.toString());
        require(info != null, "Mining information not collateralized for " + user.toString());
        Map<Long, MiningDetailInfo> detailInfos = info.getMiningDetailInfos();
        for (Long key : detailInfos.keySet()) {
            MiningDetailInfo detailInfo = detailInfos.get(key);
            if (!alreadyReceive.contains(detailInfo.getDepositorAddress())) {
                DepositInfo depositInfo = depositService.getDepositInfo(detailInfo.getDepositorAddress());
                require(depositInfo != null, "This user is not involved in the mortgage");
                this.receive(depositInfo);
                alreadyReceive.add(detailInfo.getDepositorAddress());
            }
        }
        emit(new MiningInfoEvent(info));
    }

    /**
     * View User Mining Information
     */
    @View
    public MiningInfo getMingInfo(@Required Address address) {
        return miningService.getMiningInfo(address.toString());
    }

    /**
     * View User Mortgage Information
     *
     * @return
     */
    @View
    public DepositInfo getDepositInfo(@Required Address address) {
        return depositService.getDepositInfo(address.toString());
    }

    /**
     * View Airdrop Information
     *
     * @return
     */
    @View
    public List<AirdropperInfo> getAirdropperInfo() {
        return ariDropperService.getAriDropperInfos();
    }

    /**
     * View the current price
     */
    @View
    public String currentPrice() {
        long currentHeight = Block.number();
        BigDecimal price = miningService.getCurrentPrice(currentHeight);
        return price.toPlainString() + " " + name() + "/NULS .";
    }

    /**
     * Initial price
     */
    @View
    public String initialPrice() {
        return miningService.getInitialPrice().toPlainString() + " " + name() + "/ x NULS";
    }

    @View
    public long createHeight() {
        return miningService.getCreateHeight();
    }

    @View
    public int getTotalDepositNumber() {
        return miningService.getTotalDepositList().size();
    }

    @View
    public String getTotalDepositList() {
        List<RewardCycleInfo> totalDepositList = miningService.getTotalDepositList();
        int size = totalDepositList.size();
        String depositInfo = "{";
        for (int i = 0; i < size; i++) {
            RewardCycleInfo info = totalDepositList.get(i);
            depositInfo = depositInfo + info.toString() + ",";
        }
        if (size > 0) {
            depositInfo = depositInfo.substring(0, depositInfo.length() - 1) + "}";
        } else {
            depositInfo = depositInfo + "}";
        }

        return depositInfo;
    }

    /**
     * the current reward cycle
     *
     * @return
     */
    @View
    public long currentRewardCycle() {
        return miningService.calcRewardCycle(Block.number());
    }

    @View
    public int totalDepositAddressCount() {
        return depositService.getTotalDepositAddressCount();
    }

    @View
    public String totalDeposit() {
        return toNuls(depositService.getTotalDeposit()).toPlainString();
    }

    @View
    public long awardingCycle() {
        return miningService.getAwardingCycle();
    }

    @View
    public long rewardHalvingCycle() {
        return miningService.getRewardHalvingCycle();
    }

    @View
    public BigInteger minimumDeposit() {
        return depositService.getMinimumDeposit();
    }

    @View
    public int minimumLocked() {
        return depositService.getMinimumLocked();
    }

    @View
    public int maximumDepositAddressCount() {
        return depositService.getMaximumDepositAddressCount();
    }


    /**
     * Receive awards
     *
     * @param depositInfo
     */
    private void receive(DepositInfo depositInfo) {
        Map<String, BigInteger> mingResult = new HashMap<String, BigInteger>();
        //Calculate the amount of reward
        BigInteger thisMining = miningService.calcMining(depositInfo, mingResult);
        Set<String> set = new HashSet<String>(mingResult.keySet());
        for (String address : set) {
            Address user = new Address(address);
            BigInteger mingValue = mingResult.get(address);
            addBalance(user, mingValue);
            emit(new TransferEvent(null, user, mingValue));
        }
        this.setTotalSupply(this.getTotalSupply().add(thisMining));
    }
}
