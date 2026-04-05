package com.jpmc.midascore.component;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final IncentiveService incentiveService;

    public TransactionService(UserRepository userRepository,
                               TransactionRepository transactionRepository,
                               IncentiveService incentiveService) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.incentiveService = incentiveService;
    }

    @Transactional
    public void process(Transaction transaction) {
        // Validate sender and recipient exist
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());

        if (sender == null) {
            logger.warn("Invalid senderId: {}", transaction.getSenderId());
            return;
        }
        if (recipient == null) {
            logger.warn("Invalid recipientId: {}", transaction.getRecipientId());
            return;
        }

        // Validate sender has sufficient balance
        if (sender.getBalance() < transaction.getAmount()) {
            logger.warn("Insufficient balance for sender {}: balance={}, amount={}",
                    sender.getName(), sender.getBalance(), transaction.getAmount());
            return;
        }

        // Fetch incentive from external API (only for valid transactions)
        float incentiveAmount = incentiveService.getIncentive(transaction);

        // Apply transaction:
        // - Deduct amount from sender
        // - Credit amount + incentive to recipient (incentive NOT deducted from sender)
        sender.setBalance(sender.getBalance() - transaction.getAmount());
        recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);

        userRepository.save(sender);
        userRepository.save(recipient);

        TransactionRecord record = new TransactionRecord(sender, recipient, transaction.getAmount(), incentiveAmount);
        transactionRepository.save(record);

        logger.info("Processed: {} -> {} | amount={} | incentive={} | sender balance={} | recipient balance={}",
                sender.getName(), recipient.getName(), transaction.getAmount(), incentiveAmount,
                sender.getBalance(), recipient.getBalance());
    }
}
