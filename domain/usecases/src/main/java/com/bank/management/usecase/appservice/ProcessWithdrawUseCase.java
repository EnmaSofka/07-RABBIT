package com.bank.management.usecase.appservice;

import com.bank.management.*;
import com.bank.management.exception.BankAccountNotFoundException;
import com.bank.management.exception.CustomerNotFoundException;
import com.bank.management.exception.InsufficientFundsException;
import com.bank.management.exception.InvalidAmountException;
import com.bank.management.gateway.AccountRepository;
import com.bank.management.gateway.CustomerRepository;
import com.bank.management.gateway.MessageSenderGateway;
import com.bank.management.gateway.TransactionRepository;

import java.math.BigDecimal;
import java.util.Optional;

public class ProcessWithdrawUseCase {


    private final AccountRepository bankAccountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final MessageSenderGateway messageSenderGateway;

    public ProcessWithdrawUseCase(AccountRepository bankAccountRepository, CustomerRepository customerRepository, TransactionRepository transactionRepository, MessageSenderGateway messageSenderGateway) {
        this.bankAccountRepository = bankAccountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.messageSenderGateway = messageSenderGateway;
    }

    public Optional<Account> apply(Withdrawal withdrawal) {
        if (withdrawal.getAmount() == null || withdrawal.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            InvalidAmountException exception = new InvalidAmountException();
            String errorMessage = exception.getMessage();
            messageSenderGateway.sendMessageError(errorMessage);
            throw exception;
        }

        Optional<Account> accountOptional = bankAccountRepository.findByNumber(withdrawal.getAccountNumber());
        Optional<Customer> customerOptional = customerRepository.findByUsername(withdrawal.getUsername());
        if (accountOptional.isEmpty()) {
            BankAccountNotFoundException exception = new BankAccountNotFoundException();
            String errorMessage = exception.getMessage();
            messageSenderGateway.sendMessageError(errorMessage);
            throw exception;
        }

        if (customerOptional.isEmpty()) {
            CustomerNotFoundException exception = new CustomerNotFoundException(withdrawal.getUsername());
            String errorMessage = exception.getMessage();
            messageSenderGateway.sendMessageError(errorMessage);
            throw exception;
        }

        BigDecimal transactionFee = new BigDecimal("1.00");
        BigDecimal totalCharge = withdrawal.getAmount().add(transactionFee);

        Account account = accountOptional.get();
        Customer customer = customerOptional.get();

        if (account.getAmount().compareTo(totalCharge) < 0) {
            InsufficientFundsException exception = new InsufficientFundsException();
            String errorMessage = exception.getMessage();
            messageSenderGateway.sendMessageError(errorMessage);
            throw exception;
        }

        account.adjustBalance(totalCharge.negate());
        Transaction trx = new Transaction.Builder()
                .amountTransaction(withdrawal.getAmount())
                .transactionCost(transactionFee)
                .typeTransaction("WITHDRAWAL")
                .build();

        Optional<Transaction> trxSaved = transactionRepository.save(trx, account, customer ,"RECEIVED");
        Optional<Account> accountSaved = bankAccountRepository.save(account);
        trxSaved.ifPresent(messageSenderGateway::sendTransactionSuccess);
        return accountSaved;
    }

}
