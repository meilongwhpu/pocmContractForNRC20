package io.nuls.pocm.contract.ownership;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Event;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.annotation.View;

import static io.nuls.contract.sdk.Utils.emit;
import static io.nuls.contract.sdk.Utils.require;

/**
 * @author: Long
 * @date: 2019-03-15
 */
public class Ownable {

    /**
     * Contract Creator
     */
    protected Address contractCreator;

    /**
     * Contract Owner
     */
    protected Address owner;

    public Ownable() {
        this.owner = Msg.sender();
        this.contractCreator = this.owner;
    }

    @View
    public Address viewOwner() {
        return owner;
    }

    @View
    public String viewContractCreator() {
        return this.contractCreator != null ? this.contractCreator.toString() : "";
    }

    protected void onlyOwner() {
        require(Msg.sender().equals(owner), "Only the owner of the contract can execute it.");
    }

    /**
     * Transfer of contract ownership
     *
     * @param newOwner
     */
    public void transferOwnership(Address newOwner) {
        onlyOwner();
        emit(new OwnershipTransferredEvent(owner, newOwner));
        owner = newOwner;
    }

    /**
     * Give up the contract
     */
    public void renounceOwnership() {
        onlyOwner();
        emit(new OwnershipRenouncedEvent(owner));
        owner = null;
    }

    /**
     * Transfer of Contract Ownership Event
     */
    class OwnershipTransferredEvent implements Event {

        /**
         * Previous owners
         */
        private Address previousOwner;

        /**
         * New owners
         */
        private Address newOwner;

        public OwnershipTransferredEvent(Address previousOwner, Address newOwner) {
            this.previousOwner = previousOwner;
            this.newOwner = newOwner;
        }

        public Address getPreviousOwner() {
            return previousOwner;
        }

        public void setPreviousOwner(Address previousOwner) {
            this.previousOwner = previousOwner;
        }

        public Address getNewOwner() {
            return newOwner;
        }

        public void setNewOwner(Address newOwner) {
            this.newOwner = newOwner;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            OwnershipTransferredEvent that = (OwnershipTransferredEvent) o;

            if (previousOwner != null ? !previousOwner.equals(that.previousOwner) : that.previousOwner != null) {
                return false;
            }

            return newOwner != null ? newOwner.equals(that.newOwner) : that.newOwner == null;
        }

        @Override
        public int hashCode() {
            int result = previousOwner != null ? previousOwner.hashCode() : 0;
            result = 31 * result + (newOwner != null ? newOwner.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "OwnershipTransferredEvent{" +
                    "previousOwner=" + previousOwner +
                    ", newOwner=" + newOwner +
                    '}';
        }

    }


    /**
     * 放弃拥有者
     */
    class OwnershipRenouncedEvent implements Event {

        // 先前拥有者
        private Address previousOwner;

        public OwnershipRenouncedEvent(Address previousOwner) {
            this.previousOwner = previousOwner;
        }

        public Address getPreviousOwner() {
            return previousOwner;
        }

        public void setPreviousOwner(Address previousOwner) {
            this.previousOwner = previousOwner;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            OwnershipRenouncedEvent that = (OwnershipRenouncedEvent) o;

            return previousOwner != null ? previousOwner.equals(that.previousOwner) : that.previousOwner == null;
        }

        @Override
        public int hashCode() {
            return previousOwner != null ? previousOwner.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "OwnershipRenouncedEvent{" +
                    "previousOwner=" + previousOwner +
                    '}';
        }

    }

}
