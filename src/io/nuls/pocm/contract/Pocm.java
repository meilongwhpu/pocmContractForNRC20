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

    // 合约创建高度
    private final long createHeight;
    // 初始价格，每个NULS可挖出XX个token
    private BigDecimal initialPrice;
    // 奖励发放周期（参数类型为数字，每过XXXX块发放一次）
    private int awardingCycle;
    // 奖励减半周期（可选参数，若选择，则参数类型为数字，每XXXXX块奖励减半）
    private int rewardHalvingCycle;
    // 最低抵押na数量(1亿个na等于1个NULS）
    private BigInteger minimumDeposit;
    // 最短锁定区块（参数类型为数字，XXXXX块后才可退出抵押）
    private int minimumLocked;
    // 最大抵押地址数量（可选参数）
    private int maximumDepositAddressCount;

    //接收空投地址列表
    private List<AirdropperInfo> ariDropperInfos = new ArrayList<AirdropperInfo>();

    //用户抵押信息(key为抵押者地址）
    private Map<String, DepositInfo> depositUsers = new HashMap<String, DepositInfo>();

    // 用户挖矿信息(key为接收挖矿Token地址）
    private Map<String, MiningInfo> mingUsers = new HashMap<String, MiningInfo>();

    //历史挖矿信息，暂未使用
    private Map<String, MiningInfo> mingUserHis=  new HashMap<String, MiningInfo>();

    // 总抵押金额
    private BigInteger totalDeposit;
    // 总抵押地址数量
    private int totalDepositAddressCount;

    private static long NUMBER=1L;


    public Pocm(@Required String name,@Required String symbol, @Required BigInteger initialAmount, @Required int decimals, @Required BigDecimal price, @Required int awardingCycle,
                @Required BigDecimal minimumDepositNULS, @Required int minimumLocked,int rewardHalvingCycle, int maximumDepositAddressCount, String[] receiverAddress, long[] receiverAmount) {
        super(name, symbol, initialAmount, decimals,receiverAddress,receiverAmount);
        // 检查 price 小数位不得大于decimals
        require(checkMaximumDecimals(price, decimals), "最多" + decimals + "位小数");
        require(minimumLocked>0,"最短锁定区块值应该大于0");
        require(rewardHalvingCycle>=0,"奖励减半周期应该大于等于0");
        require(maximumDepositAddressCount>=0,"最低抵押数量应该大于等于0");
        this.createHeight = Block.number();
        this.totalDeposit = BigInteger.ZERO;
        this.totalDepositAddressCount = 0;
        this.initialPrice = price;
        this.awardingCycle = awardingCycle;
        this.rewardHalvingCycle = rewardHalvingCycle;
        this.minimumDeposit = toNa(minimumDepositNULS);
        this.minimumLocked = minimumLocked;
        this.maximumDepositAddressCount = maximumDepositAddressCount;
        BigInteger  receiverTotalAmount=BigInteger.ZERO;
        if(receiverAddress!=null && receiverAmount!=null){
            Address[] receiverAddr= convertStringToAddres(receiverAddress);
            //给接收者地址空投Token
            for(int i=0;i<receiverAddress.length;i++){
                if(receiverAddress[i].equals(Msg.sender().toString())){
                    continue;
                }
                AirdropperInfo info = new AirdropperInfo();
                info.setReceiverAddress(receiverAddress[i]);
                BigInteger receiverSupply = BigInteger.valueOf(receiverAmount[i]).multiply(BigInteger.TEN.pow(decimals));
                info.setAirdropperAmount(receiverSupply);
                ariDropperInfos.add(info);
                addBalance(receiverAddr[i],receiverSupply);
                emit(new TransferEvent(null, receiverAddr[i], receiverSupply));
                receiverTotalAmount=receiverTotalAmount.add(BigInteger.valueOf(receiverAmount[i]));
            }
        }

        BigInteger canInitialAmount=initialAmount.subtract(receiverTotalAmount);
        BigInteger initialCreaterSupply = canInitialAmount.multiply(BigInteger.TEN.pow(decimals));
        addBalance(Msg.sender(),initialCreaterSupply);
        emit(new TransferEvent(null, Msg.sender(), initialCreaterSupply));

        if(initialAmount.compareTo(receiverTotalAmount)>=0){
            AirdropperInfo info = new AirdropperInfo();
            info.setReceiverAddress(Msg.sender().toString());
            info.setAirdropperAmount(initialAmount.subtract(receiverTotalAmount).multiply(BigInteger.TEN.pow(decimals)));
            ariDropperInfos.add(info);
        }
    }
    /**
     *为自己抵押获取Token
     * @return
     */
    @Payable
    public void depositForOwn() {
        String userStr = Msg.sender().toString();
        boolean isFirst=false;
        DepositInfo info =depositUsers.get(userStr);
        if(info==null && maximumDepositAddressCount>0){
            require(totalDepositAddressCount + 1 <= maximumDepositAddressCount, "超过最大抵押地址数量");
        }
        BigInteger value = Msg.value();
        require(value.compareTo(minimumDeposit) >= 0, "未达到最低抵押值:"+minimumDeposit);
        long depositNumber =NUMBER++; //抵押编号要优化，可能同一个区块多个抵押
        if(info==null){
            info =new DepositInfo();
            isFirst=true;
        }

        DepositDetailInfo detailInfo = new DepositDetailInfo();
        detailInfo.setDepositAmount(value);
        detailInfo.setDepositHeight(Block.number());
        detailInfo.setMiningAddress(userStr);
        detailInfo.setDepositNumber(depositNumber);
        info.setDepositorAddress(userStr);
        info.getDepositDetailInfos().add(detailInfo);
        info.setDepositTotalAmount(info.getDepositTotalAmount().add(value));
        info.setDepositCount(info.getDepositCount()+1); //TODO:是否考虑并发
        if(isFirst){
            depositUsers.put(userStr,info);
            totalDepositAddressCount += 1;
        }
        //初始化挖矿信息
        initMingInfo(userStr,userStr,depositNumber);
        totalDeposit = totalDeposit.add(value);
        emit(new DepositInfoEvent(info));
    }

    /**
     *为他人抵押挖取Token
     * @param miningAddress  指定挖出Token的接受地址
     * @return
     */
    @Payable
    public void depositForOther(@Required Address miningAddress) {
        String userStr = Msg.sender().toString();
        boolean isFirst=false;
        DepositInfo info =depositUsers.get(userStr);
        if(info==null && maximumDepositAddressCount>0){
            require(totalDepositAddressCount + 1 <= maximumDepositAddressCount, "超过最大抵押地址数量");
        }

        BigInteger value = Msg.value();
        require(value.compareTo(minimumDeposit) >= 0, "未达到最低抵押值:"+minimumDeposit);
        if(info==null){
            info =new DepositInfo();
            isFirst=true;
        }
        long depositNumber =NUMBER++;
        DepositDetailInfo detailInfo = new DepositDetailInfo();
        detailInfo.setDepositAmount(value);
        detailInfo.setDepositHeight(Block.number());
        detailInfo.setMiningAddress(miningAddress.toString());
        detailInfo.setDepositNumber(depositNumber);
        info.setDepositorAddress(userStr);
        info.getDepositDetailInfos().add(detailInfo);
        info.setDepositTotalAmount(info.getDepositTotalAmount().add(value));
        info.setDepositCount(info.getDepositCount()+1);
        if(isFirst){
            depositUsers.put(userStr,info);
            totalDepositAddressCount += 1;
        }

        //初始化挖矿信息
        initMingInfo(miningAddress.toString(),userStr,depositNumber);
        totalDeposit = totalDeposit.add(value);
        emit(new DepositInfoEvent(info));
    }

    /**
     * 退出抵押挖矿，当抵押编号为0时退出全部抵押
     * @param depositNumber 抵押编号
     * @return
     */
    public void quit(int depositNumber) {
        Address user = Msg.sender();
        DepositInfo depositInfo =getDepositInfo(user.toString());
        // 发放奖励
        MiningInfo miningInfo= receive(depositInfo);
        BigInteger deposit=BigInteger.ZERO;
        //表示退出全部的抵押
        if(depositNumber==0){
            long result= checkAllDepositLocked(depositInfo);
            require(result == -1, "挖矿的NULS没有全部解锁" );
            deposit=depositInfo.getDepositTotalAmount();
            delMingInfo(depositInfo);
            depositInfo.clearDepositDetailInfos();
        }else{
            //退出某一次抵押
            DepositDetailInfo detailInfo =depositInfo.getDepositDetailInfoByNumber(depositNumber);
            long unLockedHeight = checkDepositLocked(detailInfo);
            require(unLockedHeight == -1, "挖矿锁定中, 解锁高度是 " + unLockedHeight);
            //删除挖矿信息
            mingUsers.get(detailInfo.getMiningAddress()).removeMiningDetailInfoByNumber(depositNumber);
            depositInfo.removeDepositDetailInfoByNumber(depositNumber);
            // 退押金
            deposit = detailInfo.getDepositAmount();
            depositInfo.setDepositTotalAmount(depositInfo.getDepositTotalAmount().subtract(deposit));
            depositInfo.setDepositCount(depositInfo.getDepositCount()-1);
        }
        totalDeposit = totalDeposit.subtract(deposit);
        if(depositInfo.getDepositDetailInfos().size()==0){
            totalDepositAddressCount -= 1;
            //TODO pierre 退出后是否保留该账户的挖矿记录
            depositUsers.remove(user.toString());
        }
        Msg.sender().transfer(deposit);
        emit(new MiningInfoEvent(miningInfo));
    }

    /**
     *  领取奖励,领取为自己抵押挖矿的Token
     */
    public void receiveAwards() {
        Address user = Msg.sender();
        DepositInfo depositInfo = getDepositInfo(user.toString());
        MiningInfo miningInfo= this.receive(depositInfo);
        emit(new MiningInfoEvent(miningInfo));
    }

    /**
     * 由挖矿接收地址发起领取奖励;当抵押用户为其他用户做抵押挖矿时，接收token用户可以发起此方法
     * @return
     */
    public void receiveAwardsForMiningAddress(){
        Address user = Msg.sender();
        MiningInfo info =getMiningInfo(user.toString());
        List<MiningDetailInfo> detailInfos= info.getMiningDetailInfo();
        List<String> alreadyReceive = new ArrayList<String>();
        for(int i=0;i<detailInfos.size();i++){
            String address =detailInfos.get(i).getDepositorAddress();
            if(!alreadyReceive.contains(address)){
                DepositInfo depositInfo = getDepositInfo(address);
                this.receive(depositInfo);
                alreadyReceive.add(address);
            }
        }
        emit(new MiningInfoEvent(info));
    }

    /**
     *  查找用户挖矿信息
     */
    @View
    public MiningInfo getMingInfo(@Required Address address) {
        return getMiningInfo(address.toString());
    }

    /**
     * 查找用户的抵押信息
     * @return
     */
    @View
    public DepositInfo getDepositInfo(@Required Address address){
        return getDepositInfo(address.toString());
    }

    /**
     * 获取空投信息
     * @return
     */
    @View
    public List<AirdropperInfo> getAirdropperInfo(){
        return ariDropperInfos;
    }

    /**
     *  当前价格
     */
    @View
    public String currentPrice() {
        long currentHeight = Block.number();
        BigDecimal currentPrice = this.calcPriceSeed(currentHeight);
        return currentPrice.toPlainString() + " " + name() + "/NULS";
    }

    /**
     *单价的精度不能超过定义的精度
     * @param price 单价
     * @param decimals 精度
     * @return
     */
    private static boolean checkMaximumDecimals(BigDecimal price, int decimals) {
        BigInteger a = price.movePointRight(decimals).toBigInteger().multiply(BigInteger.TEN);
        BigInteger b = price.movePointRight(decimals + 1).toBigInteger();
        if(a.compareTo(b) != 0) {
            return false;
        }
        return true;
    }


    /**
     * 根据挖矿地址从队列中获取挖矿信息
     * @param userStr
     * @return
     */
    private MiningInfo getMiningInfo(String userStr) {
        MiningInfo miningInfo = mingUsers.get(userStr);
        require(miningInfo != null, "没有此用户（或为此用户）挖矿的信息");
        return miningInfo;
    }

    /**
     * 根据抵押地址从队列中获取抵押信息
     * @param userStr
     * @return
     */
    private DepositInfo getDepositInfo(String userStr) {
        DepositInfo depositInfo = depositUsers.get(userStr);
        require(depositInfo != null, "此用户未参与抵押");
        return depositInfo;
    }

    /**
     * 检查抵押是否在锁定中
     * @param depositInfo
     * @return
     */
    private long checkAllDepositLocked(DepositInfo depositInfo) {
        long result;
        List<DepositDetailInfo> infos =depositInfo.getDepositDetailInfos();
        for(int i=0;i<infos.size();i++){
            result =checkDepositLocked(infos.get(i));
            if(result!=-1){
                return result;
            }
        }
        return -1;
    }

    /**
     * 检查抵押是否在锁定中
     * @param detailInfo
     * @return
     */
    private long checkDepositLocked(DepositDetailInfo detailInfo) {
        long currentHeight = Block.number();
        long unLockedHeight = detailInfo.getDepositHeight() + minimumLocked + 1;
        if(unLockedHeight > currentHeight) {
            // 锁定中
            return unLockedHeight;
        }
        //已解锁
        return -1;
    }


    /**
     * 领取奖励
     * @param depositInfo
     * @return 返回请求地址的挖矿信息
     */
    private MiningInfo receive(DepositInfo depositInfo) {
        Map<String,BigInteger> mingResult= new HashMap<String, BigInteger>();
        // 奖励计算, 计算每次挖矿的高度是否已达到奖励减半周期的范围，若达到，则当次奖励减半，以此类推
        BigInteger thisMining = this.calcMining(depositInfo,mingResult);

       Set<String> set = new HashSet<String>(mingResult.keySet());
        for(String address:set){
            Address user = new Address(address);
            BigInteger mingValue= mingResult.get(address);
            addBalance(user, mingValue);
            emit(new TransferEvent(null, user, mingValue));
        }

        this.setTotalSupply(this.getTotalSupply().add(thisMining));

        return getMiningInfo(depositInfo.getDepositorAddress());
    }


    /**
     * 计算奖励数额
     * @param depositInfo
     * @param mingResult
     * @return
     */
    private BigInteger calcMining(DepositInfo depositInfo,Map<String,BigInteger> mingResult) {
        BigInteger mining = BigInteger.ZERO;
        BigDecimal currentPrice;
        long currentHeight = Block.number();
        List<DepositDetailInfo> detailInfoList =depositInfo.getDepositDetailInfos();
        for(int i=0;i<detailInfoList.size();i++){
            int round=0;
            BigInteger mining_tmp=BigInteger.ZERO;
            DepositDetailInfo detailInfo=detailInfoList.get(i);
            MiningInfo miningInfo = getMiningInfo(detailInfo.getMiningAddress());
            MiningDetailInfo mingDetailInfo = miningInfo.getMiningDetailInfoByNumber(detailInfo.getDepositNumber());
            long nextMiningHeight = mingDetailInfo.getNextMiningHeight();
            long depositHeight =detailInfo.getDepositHeight();
            BigDecimal depositAmountNULS = toNuls(detailInfo.getDepositAmount());
            if(nextMiningHeight == 0) {
                nextMiningHeight = depositHeight + awardingCycle + 1;
            }
            while (nextMiningHeight <= currentHeight) {
                round++;
                currentPrice = calcPriceSeed(nextMiningHeight);
                mining_tmp = mining_tmp.add(depositAmountNULS.multiply(currentPrice).scaleByPowerOfTen(decimals()).toBigInteger());
                nextMiningHeight += awardingCycle + 1;
            }
            mining = mining.add(mining_tmp);
            mingDetailInfo.setMiningAmount(mingDetailInfo.getMiningAmount().add(mining_tmp));
            mingDetailInfo.setMiningCount(mingDetailInfo.getMiningCount()+round);
            mingDetailInfo.setNextMiningHeight(nextMiningHeight);
            miningInfo.setTotalMining(miningInfo.getTotalMining().add(mining_tmp));
            miningInfo.setReceivedMining(miningInfo.getReceivedMining().add(mining_tmp));

            if(mingResult.containsKey(mingDetailInfo.getReceiverMiningAddress())){
                mining_tmp=mingResult.get(mingDetailInfo.getReceiverMiningAddress()).add(mining_tmp);
            }
            mingResult.put(mingDetailInfo.getReceiverMiningAddress(),mining_tmp);
        }
        return mining;
    }


    /**
     * 计算当前奖励周期的单价
     * @param currentHeight
     * @return
     */
    private BigDecimal calcPriceSeed(long currentHeight) {
        BigDecimal currentPrice = this.initialPrice;
        if(rewardHalvingCycle==0){
            return currentPrice;
        }
        long triggerHeight = this.createHeight + this.rewardHalvingCycle + 1;
        BigDecimal d = BigDecimal.valueOf(2L);
        while(triggerHeight <= currentHeight) {
            currentPrice = currentPrice.divide(d);
            triggerHeight += this.rewardHalvingCycle + 1;
        }
        return currentPrice;
    }

    /**
     * 删除挖矿信息
     * @param depositInfo
     */
    private void delMingInfo(DepositInfo depositInfo){
        List<DepositDetailInfo> depositDetailInfos = depositInfo.getDepositDetailInfos();
        for(int i=0;i<depositDetailInfos.size();i++){
            List<MiningDetailInfo> miningDetailInfo =mingUsers.get(depositDetailInfos.get(i).getMiningAddress()).getMiningDetailInfo();
            for(int j=0;j<miningDetailInfo.size();j++){
                if(depositDetailInfos.get(i).getDepositNumber() ==miningDetailInfo.get(j).getDepositNumber()){
                    miningDetailInfo.remove(j);
                    break;
                }
            }
        }
    }

    /**
     * 初始化挖矿信息
     * @param miningAddress
     * @param depositorAddress
     * @param depositNumber
     * @return
     */
    private MiningInfo initMingInfo(String miningAddress ,String depositorAddress,long depositNumber){
        MiningDetailInfo mingDetailInfo = new MiningDetailInfo(miningAddress,depositorAddress,depositNumber);
        MiningInfo mingInfo =  mingUsers.get(miningAddress);
        if(mingInfo==null){//该Token地址为第一次挖矿
            mingInfo =  new MiningInfo();
            mingInfo.getMiningDetailInfo().add(mingDetailInfo);
            mingUsers.put(miningAddress,mingInfo);
        }else{
            mingInfo.getMiningDetailInfo().add(mingDetailInfo);
        }
        return mingInfo;
    }

    /**
     *  初始价格
     */
    @View
    public String initialPrice() {
        return initialPrice.toPlainString() + " " + name() + "/NULS";
    }

    @View
    public long createHeight() {
        return createHeight;
    }

    @View
    public int totalDepositAddressCount() {
        return totalDepositAddressCount;
    }

    @View
    public String totalDeposit() {
        return toNuls(totalDeposit).toPlainString();
    }

    @View
    public long awardingCycle() {
        return this.awardingCycle;
    }
    @View
    public long rewardHalvingCycle() {
        return this.rewardHalvingCycle;
    }
    @View
    public BigInteger minimumDeposit() {
        return this.minimumDeposit;
    }
    @View
    public int minimumLocked() {
        return this.minimumLocked;
    }
    @View
    public int maximumDepositAddressCount() {
        return this.maximumDepositAddressCount;
    }

    }
