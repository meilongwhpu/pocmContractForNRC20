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
        // 检查 price 小数位不得大于decimals
        require(price.compareTo(BigDecimal.ZERO) > 0, "价格应该大于0");
        require(checkMaximumDecimals(price, decimals), "最多" + decimals + "位小数");
        require(minimumLocked > 0, "最短锁定区块值应该大于0");
        require(awardingCycle > 0, "奖励发放周期应该大于0");
        int rewardHalvingCycleForInt = 0;
        int maximumDepositAddressCountForInt = 0;
        if (rewardHalvingCycle != null && rewardHalvingCycle.trim().length() > 0) {
            require(canConvertNumeric(rewardHalvingCycle.trim(), String.valueOf(Integer.MAX_VALUE)), "奖励减半周期输入不合法，应该输入小于2147483647的数字字符");
            rewardHalvingCycleForInt = Integer.parseInt(rewardHalvingCycle.trim());
            require(rewardHalvingCycleForInt >= 0, "奖励减半周期应该大于等于0");
        }
        if (maximumDepositAddressCount != null && maximumDepositAddressCount.trim().length() > 0) {
            require(canConvertNumeric(maximumDepositAddressCount.trim(), String.valueOf(Integer.MAX_VALUE)), "最低抵押数量输入不合法，应该输入小于2147483647的数字字符");
            maximumDepositAddressCountForInt = Integer.parseInt(maximumDepositAddressCount.trim());
            require(maximumDepositAddressCountForInt >= 0, "最低抵押数量应该大于等于0");
        }

        depositService = new DepositService(minimumLocked, toNa(minimumDepositNULS), maximumDepositAddressCountForInt);
        miningService = new MiningService(Block.number(), awardingCycle, rewardHalvingCycleForInt, decimals, price);
        ariDropperService = new AriDropperService();

        BigInteger receiverTotalAmount = BigInteger.ZERO;
        if (receiverAddress != null && receiverAmount != null) {
            Address[] receiverAddr = convertStringToAddres(receiverAddress);
            //给接收者地址空投Token
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
     * 为自己抵押获取Token
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

        //将抵押数加入队列中
        miningService.putDeposit(value, currentHeight);

        //初始化挖矿信息
        miningService.initMingInfo(currentHeight, userStr, userStr, depositNumber);

        emit(new DepositInfoEvent(info));
    }

    /**
     * 为他人抵押挖取Token
     *
     * @param miningAddress 指定挖出Token的接受地址
     * @return
     */
    @Payable
    public void depositForOther(@Required Address miningAddress) {
        String userStr = Msg.sender().toString();
        BigInteger value = Msg.value();
        long currentHeight = Block.number();
        long depositNumber = NUMBER++;
        DepositInfo info = depositService.addDeposit(userStr, miningAddress.toString(), value, currentHeight, depositNumber);

        //将抵押数加入队列中
        miningService.putDeposit(value, currentHeight);

        //初始化挖矿信息
        miningService.initMingInfo(currentHeight, miningAddress.toString(), userStr, depositNumber);

        emit(new DepositInfoEvent(info));
    }

    /**
     * 退出抵押挖矿，当抵押编号为0时退出全部抵押
     *
     * @param number 抵押编号
     * @return
     */
    public void quit(String number) {
        long currentHeight = Block.number();
        String userStr = Msg.sender().toString();
        long depositNumber = 0;
        if (number != null && number.trim().length() > 0) {
            require(canConvertNumeric(number.trim(), String.valueOf(Long.MAX_VALUE)), "抵押编号输入不合法，应该输入数字字符");
            depositNumber = Long.valueOf(number.trim());
        }
        DepositInfo depositInfo = depositService.getDepositInfo(userStr);
        require(depositInfo != null, "此用户未参与抵押");

        // 发放奖励
        this.receive(depositInfo);

        BigInteger deposit;
        MiningInfo miningInfo;

        //表示退出全部的抵押
        if (depositNumber == 0) {
            miningInfo = miningService.getMiningInfo(depositInfo.getDepositorAddress());
            long result = depositService.checkAllDepositLocked(depositInfo);
            require(result == -1, "挖矿的NULS没有全部解锁");

            deposit = depositInfo.getDepositTotalAmount();
            miningService.removeAllMiningInfo(depositInfo);

            Map<Long, DepositDetailInfo> depositDetailInfos = depositInfo.getDepositDetailInfos();

            //从队列中退出抵押金额
            for (Long key : depositDetailInfos.keySet()) {
                DepositDetailInfo detailInfo = depositDetailInfos.get(key);
                miningService.quitDeposit(detailInfo, currentHeight);
            }

            depositService.clearDepositDetailInfos(depositInfo);
        } else {
            //退出某一次抵押
            DepositDetailInfo detailInfo = depositInfo.getDepositDetailInfoByNumber(depositNumber);

            miningInfo = miningService.getMiningInfo(detailInfo.getMiningAddress());

            long unLockedHeight = depositService.checkDepositLocked(detailInfo);
            require(unLockedHeight == -1, "挖矿锁定中, 解锁高度是 " + unLockedHeight);

            //删除挖矿信息
            miningService.removeMiningInfo(detailInfo.getMiningAddress(), depositNumber);

            //删除抵押信息
            depositInfo.removeDepositDetailInfoByNumber(depositNumber);

            // 退押金
            deposit = detailInfo.getDepositAmount();
            depositInfo.setDepositTotalAmount(depositInfo.getDepositTotalAmount().subtract(deposit));
            depositInfo.setDepositCount(depositInfo.getDepositCount() - 1);
            //从队列中退出抵押金额
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
     * 领取奖励,领取为自己抵押挖矿的Token
     */
    public void receiveAwards() {
        Address user = Msg.sender();
        MiningInfo miningInfo = miningService.getMiningInfo(user.toString());
        require(miningInfo != null, "没有为自己抵押挖矿的挖矿信息");
        DepositInfo depositInfo = depositService.getDepositInfo(user.toString());
        require(depositInfo != null, "此用户未参与抵押");
        this.receive(depositInfo);
        emit(new MiningInfoEvent(miningInfo));
    }

    /**
     * 由挖矿接收地址发起领取奖励;当抵押用户为其他用户做抵押挖矿时，接收token用户可以发起此方法
     *
     * @return
     */
    public void receiveAwardsForMiningAddress() {
        List<String> alreadyReceive = new ArrayList<String>();
        Address user = Msg.sender();
        MiningInfo info = miningService.getMiningInfo(user.toString());
        require(info != null, "没有替" + user.toString() + "用户抵押挖矿的挖矿信息");
        Map<Long, MiningDetailInfo> detailInfos = info.getMiningDetailInfos();
        for (Long key : detailInfos.keySet()) {
            MiningDetailInfo detailInfo = detailInfos.get(key);
            if (!alreadyReceive.contains(detailInfo.getDepositorAddress())) {
                DepositInfo depositInfo = depositService.getDepositInfo(detailInfo.getDepositorAddress());
                require(depositInfo != null, "此用户未参与抵押");
                this.receive(depositInfo);
                alreadyReceive.add(detailInfo.getDepositorAddress());
            }
        }
        emit(new MiningInfoEvent(info));
    }

    /**
     * 查找用户挖矿信息
     */
    @View
    public MiningInfo getMingInfo(@Required Address address) {
        return miningService.getMiningInfo(address.toString());
    }

    /**
     * 查找用户的抵押信息
     *
     * @return
     */
    @View
    public DepositInfo getDepositInfo(@Required Address address) {
        return depositService.getDepositInfo(address.toString());
    }

    /**
     * 获取空投信息
     *
     * @return
     */
    @View
    public List<AirdropperInfo> getAirdropperInfo() {
        return ariDropperService.getAriDropperInfos();
    }

    /**
     * 当前价格
     */
    @View
    public String currentPrice() {
        long currentHeight = Block.number();
        BigDecimal price = miningService.getCurrentPrice(currentHeight);
        return price.toPlainString() + " " + name() + "/NULS .";
    }

    /**
     * 初始价格
     */
    @View
    public String initialPrice() {
        return miningService.getinitialPrice().toPlainString() + " " + name() + "/ x NULS";
    }

    @View
    public long createHeight() {
        return miningService.getCreateHeight();
    }

    @View
    public int getTotalDepositNumber(){
        return  miningService.getTotalDepositList().size();
    }

    @View
    public String getTotalDepositList() {
        List<RewardCycleInfo> totalDepositList = miningService.getTotalDepositList();
        int size =totalDepositList.size();
        String depositInfo = "{";
        for (int i = 0; i < size; i++) {
            RewardCycleInfo info = totalDepositList.get(i);
            depositInfo = depositInfo + info.toString() + ",";
        }
        if(size>0){
            depositInfo = depositInfo.substring(0, depositInfo.length() - 1) + "}";
        }else{
            depositInfo = depositInfo + "}";
        }

        return depositInfo;
    }

    /**
     * 当前奖励周期
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
     * 领取奖励
     *
     * @param depositInfo
     * @return 返回请求地址的挖矿信息
     */
    private void receive(DepositInfo depositInfo) {
        Map<String, BigInteger> mingResult = new HashMap<String, BigInteger>();
        // 奖励计算, 计算每次挖矿的高度是否已达到奖励减半周期的范围，若达到，则当次奖励减半，以此类推
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
