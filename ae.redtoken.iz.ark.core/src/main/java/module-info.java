import ae.redtoken.iz.ark.nostrtest.EventCustomHandler2;

module ae.redtoken.iz.keyvault {
    requires nostr.command.handler;
    requires static lombok;
    requires nostr.base;
    requires nostr.context;
    requires nostr.event;
    requires java.logging;
    requires nostr.context.impl;
    requires org.slf4j;
    requires nostr.util;
    requires org.bitcoinj.core;
    requires nostr.crypto;
    requires com.fasterxml.jackson.databind;
    requires org.bouncycastle.pg;
    requires org.bouncycastle.pkix;
    provides nostr.command.CommandHandler with EventCustomHandler2;
    exports ae.redtoken.iz.ark.nostrtest to com.fasterxml.jackson.databind;
}