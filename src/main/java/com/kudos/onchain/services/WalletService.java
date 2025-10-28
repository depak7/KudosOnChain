package com.kudos.onchain.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.kudos.onchain.model.KDPartner;
import com.kudos.onchain.model.PTEmployee;
import com.kudos.onchain.model.Wallet;
import com.kudos.onchain.repository.KDPartnerRepo;
import com.kudos.onchain.repository.PTEmployeeRepo;
import com.kudos.onchain.repository.WalletRepo;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
public class WalletService {

    @Autowired
    private WalletRepo walletRepo;

    @Autowired
    private KDPartnerRepo kdPartnerRepo;
    @Autowired
    private PTEmployeeRepo pteEmployeeRepo;

    private static final Logger LOGGER = LoggerFactory.getLogger(SlackService.class);

    @Value(("${bot.token}"))
    private String               BOT_TOKEN;

    private RestTemplate restTemplate = new RestTemplate();

    private Wallet createWallet() {
        Wallet wallet = new Wallet();
        try {
            Account account = new Account();
            wallet.setWalletAddress(account.getPublicKey().toBase58());
            String base64SecretKey = Base64.getEncoder().encodeToString(account.getSecretKey());
            wallet.setPrivateKey(base64SecretKey);
        } catch (Exception e) {
            LOGGER.error("Error in createWallet", e);
        }
        return wallet;
    }

    public String getBalance(String walletAddress) {
        try {
            PublicKey publicKey = new PublicKey(walletAddress);
            RpcClient rpcClient = new RpcClient(Cluster.DEVNET);
            long lamports = rpcClient.getApi().getBalance(publicKey);
            double sol = lamports / 1_000_000_000.0;
            return String.format("%.4f SOL", sol);
        } catch (Exception e) {
            LOGGER.error("Error in getBalance", e);
        }
        return "";
    }


    public boolean configureWallet(String teamId, String userId) {
        try {
            KDPartner partner = kdPartnerRepo.findByTeamId(teamId);
            if (partner == null) {
                LOGGER.error("KDPartner not found for teamId: {}", teamId);
                return false;
            }
            String username = getUserName(userId);
            PTEmployee employee = pteEmployeeRepo.getPTEmployeeByUserId(userId);
            if (employee == null) {
                employee = new PTEmployee();
                employee.setUserId(userId);
                employee.setActive(true);
                employee.setKdPartner(partner);
                employee.setUserName(username);
                pteEmployeeRepo.save(employee);
            }
            Wallet wallet = employee.getWallet();
            if (wallet == null) {
                wallet = createWallet();
                employee.setWallet(wallet);
            }
            employee.setWallet(wallet);
            pteEmployeeRepo.save(employee);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to handle wallet submission", e);
            return false;
        }
    }

    public String getUserName(String userId) {
        try {
            String url = "https://slack.com/api/users.info?user=" + userId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(BOT_TOKEN);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            if (response.getBody() != null && response.getBody().path("ok").asBoolean()) {
                JsonNode profile = response.getBody().path("user").path("profile");
                String displayName = profile.path("display_name").asText();
                String realName = profile.path("real_name").asText();
                return !displayName.isEmpty() ? displayName : realName;
            } else {
                LOGGER.warn("Failed to fetch user info: {}", response.getBody());
                return "Unknown User";
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching user info", e);
            return "Unknown User";
        }
    }

    public Wallet getEmployeeWallet(String userId) {
        return walletRepo.findWalletByMember_UserId(userId);
    }

    public List<PTEmployee> getEmployeeOfPartner(String teamId) {
        KDPartner partner = kdPartnerRepo.findByTeamId(teamId);
        if (partner == null) {
            LOGGER.error("KDPartner not found for teamId: {}", teamId);
            return List.of();
        }
        return pteEmployeeRepo.findByKdPartner(partner);
    }

    public String getWalletBalance(String userId) {
        Wallet wallet = getEmployeeWallet(userId);
        if (wallet == null) {
            LOGGER.error("Wallet not found for userId: {}", userId);
            return "Wallet not found";
        }
        String walletAddress = wallet.getWalletAddress();
        return getBalance(walletAddress);
    }

}
