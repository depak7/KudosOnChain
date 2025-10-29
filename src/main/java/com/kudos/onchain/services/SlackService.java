package com.kudos.onchain.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kudos.onchain.model.KDPartner;
import com.kudos.onchain.model.OrganizationNfts;
import com.kudos.onchain.model.PTEmployee;
import com.kudos.onchain.model.Wallet;
import com.kudos.onchain.repository.KDPartnerRepo;
import com.kudos.onchain.repository.OrganizationNftsRepo;
import com.kudos.onchain.repository.PTEmployeeRepo;
import org.bouncycastle.LICENSE;
import org.p2p.solanaj.core.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
public class SlackService {

    @Value("${slack.client-id}")
    private String               clientId;

    @Value("${slack.redirect-uri}")
    private String               redirectUri;

    @Value("${slack.client.secret}")
    private String               clientSecret;

    @Value("${supabase.url}")
    private String               SUPABASE_URL;
    @Value("${supabase.api.key}")
    private String               SUPABASE_API_KEY;
    @Value("${supabase.bucket.name}")
    private String               SUPABASE_BUCKET;
    @Value(("${bot.token}"))
    private String               BOT_TOKEN;

    @Autowired
    private KDPartnerRepo        kdPartnerRepo;

    @Autowired
    private WalletService        walletService;

    @Autowired
    private PTEmployeeRepo       employeeRepo;

    @Autowired
    private OrganizationNftsRepo organizationNftsRepo;

    @Autowired
    private ObjectMapper         objectMapper;

    private static final Logger  LOGGER       = LoggerFactory.getLogger(SlackService.class);
    private final RestTemplate   restTemplate = new RestTemplate();

    public ObjectNode exchangeCodeToAccessToken(String code) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "failure");
        try {
            String url = "https://slack.com/api/oauth.v2.access";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = String.format("client_id=%s&client_secret=%s&code=%s&redirect_uri=%s", clientId, clientSecret, code, redirectUri);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JsonNode json = objectMapper.readTree(response.getBody());
            if (!json.path("ok").asBoolean(false)) {
                LOGGER.error("Failed to exchange code for access token: {}", json);
                result.put("message", "OAuth exchange failed");
                return result;
            }

            JsonNode teamNode = json.path("team");
            JsonNode authedUserNode = json.path("authed_user");

            KDPartner partner = KDPartner.builder().teamId(teamNode.path("id").asText(null)).teamName(teamNode.path("name").asText(null))
                    .accessToken(json.path("access_token").asText(null)).botUserId(json.path("bot_user_id").asText(null))
                    .authedUserId(authedUserNode.path("id").asText(null)).scope(json.path("scope").asText(null))
                    .enterpriseId(json.path("enterprise_id").asText(null)).build();
            kdPartnerRepo.save(partner);
            result.put("status", "success");
            LOGGER.info("Successfully exchanged code for access token for team: {}", partner.getTeamName());
        } catch (Exception e) {
            LOGGER.error("Failed to exchange code for access token: {}", e);
        }
        return result;
    }

    public RedirectView installApp() {
        String scopes = "commands,chat:write,users:read,im:write";
        String slackAuthUrl = String.format("https://slack.com/oauth/v2/authorize?client_id=%s&scope=%s&redirect_uri=%s", clientId, scopes,
                redirectUri);
        return new RedirectView(slackAuthUrl);
    }

    public String handleSlackEvents(ObjectNode json) {
        try {
            String type = json.path("type").asText();
            LOGGER.info("Incoming Slack Event: {}", json);

            if ("url_verification".equals(type)) {
                String challenge = json.path("challenge").asText();
                LOGGER.info("Slack URL verification challenge received");
                return challenge;
            }

            if ("event_callback".equals(type)) {
                JsonNode event = json.path("event");
                if (event.has("bot_id")) {
                    LOGGER.debug("Ignoring bot message to prevent loops");
                    return "OK";
                }
                String eventType = event.path("type").asText();
                String teamId = json.path("team_id").asText();
                KDPartner partner = kdPartnerRepo.findByTeamId(teamId);

                LOGGER.info("Received Slack event: {} from team: {}", eventType, teamId);

                switch (eventType) {
                case "app_mention":
                    handleAppMention(event, partner);
                    break;

                case "message":
                    handleMessage(event);
                    break;

                default:
                    LOGGER.info("Unhandled event type: {}", eventType);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error parsing Slack event", e);
        }
        return "OK";
    }

    private void handleAppMention(JsonNode event, KDPartner partner) {
        String user = event.path("user").asText();
        String channel = event.path("channel").asText();
        String text = event.path("text").asText();

        LOGGER.info("Mentioned by {}: {}", user, text);
        replyInThread(partner.getAccessToken(), channel, "Hey <@" + user + ">! 👋 How can I help?");
    }

    private void handleMessage(JsonNode event) {
        String text = event.path("text").asText();
        String channel = event.path("channel").asText();
        String thread = event.path("thread_ts").asText();
        String teamId = event.path("team").asText();
        String userId = event.path("user").asText();

        LOGGER.info("Message received: {}", text);

        if (text == null || text.isEmpty()) {
            replyInThread(channel, thread, "⚠️ Please provide a valid command. Type *help* for available options.");
            return;
        }

        text = text.trim().toLowerCase();

        switch (text) {
        case "configure-wallet":
            if (walletService.configureWallet(teamId, userId)) {
                replyInThread(channel, thread, "💰 Wallet configured!\nYou can now give kudos to your colleagues 🎉");
            } else {
                replyInThread(channel, thread, "❌ Failed to configure wallet. Please try again.");
            }
            break;

        case "view-organization-nfts":
            List<String> nfts = getAllNftsOfOrganization(teamId);
            replyInThread(channel, thread,
                    nfts.isEmpty() ? "No NFTs found for this organization yet." : "🖼️ Organization NFTs:\n" + String.join("\n", nfts));
            break;

        case "check-balance":
            String balance = walletService.getWalletBalance(userId);
            replyInThread(channel, thread, balance);
            break;

        case "private-key":
            Wallet wallet = walletService.getEmployeeWallet(userId);
            if (wallet != null) {
                replyInThread(channel, thread,
                        "🔐 *Your Private Key (Base64)*:\n```" + wallet.getPrivateKey() + "```\nKeep it safe and *do not share it*!");
            } else {
                replyInThread(channel, thread, "No wallet found. Please configure your wallet first using `configure-wallet`.");
            }
            break;

        case "public-key":
            Wallet userWallet = walletService.getEmployeeWallet(userId);
            if (userWallet != null) {
                replyInThread(channel, thread, "🔓 *Your Public Key (Wallet Address)*:\n```" + userWallet.getWalletAddress() + "```");
            } else {
                replyInThread(channel, thread, "No wallet found. Please configure your wallet first using `configure-wallet`.");
            }
            break;

        case "help":
            sendHelpMessage(channel, thread);
            break;

        default:
            sendHelpMessage(channel, thread);
            break;
        }
    }

    private void sendHelpMessage(String channel, String thread) {
        String helpText = """
                🤖 *Kudos Bot Help Guide*

                💰 *Wallet Commands*
                • `configure-wallet` — Configure your wallet
                • `check-balance` — Check your current SOL balance
                • `private-key` — Get your Base64 private key
                • `public-key` — Get your public wallet address
                - `view-organization-nfts` — View all NFTs of your organization

                🪙 *NFT Commands*
                • `/kudos` — Mint a new NFT as kudos
                • `/makeCollection` — Create a collection to organize NFTs
                
                💡 _Type any of the above commands in chat to get started!_
                """;

        replyInThread(channel, thread, helpText);
    }

    public void replyInThread(String channelId, String threadTs, String text) {
        try {
            String url = "https://slack.com/api/chat.postMessage";

            ObjectNode body = objectMapper.createObjectNode();
            body.put("channel", channelId); // e.g., "D09MZFMR3HQ" or "CXXXX"
            body.put("text", text); // Message content
            body.put("thread_ts", threadTs); // 🧵 Reply to this thread

            HttpEntity<String> entity = new HttpEntity<>(body.toString(), buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            LOGGER.info("Thread message sent: {}", response.getBody());
        } catch (Exception e) {
            LOGGER.error("Failed to reply in thread", e);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(BOT_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public ObjectNode handleInteractions(String payloadStr) {
        LOGGER.info("Incoming Slack Interactions: {}", payloadStr);
        ObjectNode message = objectMapper.createObjectNode();
        message.put("status", "success");
        try {
            JsonNode payload = objectMapper.readTree(payloadStr);
            String teamId = payload.path("team").path("id").asText();
            String triggerId = payload.path("trigger_id").asText();
            String type = payload.path("type").asText();
            if ("shortcut".equals(type)) {
                String callbackId = payload.path("callback_id").asText();
                String responseUrl = payload.path("response_url").asText();
                if ("nft_modal".equals(callbackId)) {
                    openMintNftModal(triggerId, teamId);
                    LOGGER.info("Opened Mint NFT Modal");
                } else if ("nft_collection".equals(callbackId)) {
                    KDPartner partner = kdPartnerRepo.findByTeamId(teamId);
                    if (partner != null && partner.getCollectionAddress() != null) {
                        sendResponseMessage(responseUrl, "Collection already created at address: " + partner.getCollectionAddress());
                    } else {
                        openCollectionMintView(triggerId);
                    }
                    return message;
                }
            } else if ("view_submission".equals(type)) {
                JsonNode view = payload.path("view");
                String callbackId = view.path("callback_id").asText();
                if ("mint_nft_modal".equals(callbackId)) {
                    handleMintNftSubmission(payload);
                    LOGGER.info("Mint NFT modal submitted");
                } else if ("nft_collection".equals(callbackId)) {
                    handleCollectionSubmission(payload);
                    LOGGER.info("Collection mint modal submitted");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error parsing Slack Interactions", e);
        }
        return message;
    }

    public void openCollectionMintView(String triggerId) {
        try {
           
            String url = "https://slack.com/api/views.open";

            ObjectNode modal = objectMapper.createObjectNode();
            modal.put("trigger_id", triggerId);

            ObjectNode view = modal.putObject("view");
            view.put("type", "modal");
            view.put("callback_id", "nft_collection");

            view.putObject("title").put("type", "plain_text").put("text", "Create Collection ");
            view.putObject("submit").put("type", "plain_text").put("text", "Submit");
            view.putObject("close").put("type", "plain_text").put("text", "Cancel");

            ArrayNode blocks = view.putArray("blocks");

            // NFT Name
            ObjectNode nameBlock = blocks.addObject();
            nameBlock.put("type", "input");
            nameBlock.put("block_id", "nft_name_block");
            nameBlock.putObject("label").put("type", "plain_text").put("text", "Collection Name");
            nameBlock.putObject("element").put("type", "plain_text_input").put("action_id", "collection_name");

            // Description
            ObjectNode descBlock = blocks.addObject();
            descBlock.put("type", "input");
            descBlock.put("block_id", "nft_desc_block");
            descBlock.putObject("label").put("type", "plain_text").put("text", "Description");
            descBlock.putObject("element").put("type", "plain_text_input").put("multiline", true).put("action_id", "nft_description");

            // Image upload instructions
            ObjectNode imageTextBlock = blocks.addObject();
            imageTextBlock.put("type", "section");
            imageTextBlock.putObject("text").put("type", "mrkdwn").put("text",
                    ":frame_with_picture: *Upload Image*\nYou can either upload an image file in this thread or click below to open the upload page.");

            // Upload button (opens external upload URL)
            ObjectNode uploadButtonBlock = blocks.addObject();
            uploadButtonBlock.put("type", "actions");
            ArrayNode elements = uploadButtonBlock.putArray("elements");
            ObjectNode uploadButton = elements.addObject();
            uploadButton.put("type", "button");
            uploadButton.put("text", objectMapper.createObjectNode().put("type", "plain_text").put("text", "Upload Image"));
            uploadButton.put("url", "http://localhost:5174/");
            uploadButton.put("action_id", "upload_image_button");

            // Image URL input (optional)
            ObjectNode imgBlock = blocks.addObject();
            imgBlock.put("type", "input");
            imgBlock.put("block_id", "nft_img_block");
            imgBlock.putObject("label").put("type", "plain_text").put("text", "Image URL (copy paste if uploaded manually)");
            imgBlock.putObject("element").put("type", "plain_text_input").put("action_id", "nft_image_url");

            // Send request to Slack API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(BOT_TOKEN);

            HttpEntity<String> entity = new HttpEntity<>(modal.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        } catch (Exception e) {
            LOGGER.error("❌ Failed to open Mint NFT Modal", e);
        }
    }

    public void handleCollectionSubmission(JsonNode payload) {
        try {
            JsonNode view = payload.path("view");
            JsonNode values = view.path("state").path("values");

            // Extract user & team info
            String teamId = payload.path("team").path("id").asText();
            String userId = payload.path("user").path("id").asText();

            // Extract inputs from modal
            String name = values.path("nft_name_block").path("collection_name").path("value").asText();
            String description = values.path("nft_desc_block").path("nft_description").path("value").asText();
            String imageUrl = values.path("nft_img_block").path("nft_image_url").path("value").asText();
            KDPartner partner = kdPartnerRepo.findByTeamId(teamId);
            partner.setCollectionAddress(
                    mintCollectionNftViaNode(name, description, imageUrl, walletService.getEmployeeWallet(userId).getPrivateKey()));
            kdPartnerRepo.save(partner);
        } catch (Exception e) {
            LOGGER.error("❌ Error handling collection mint submission", e);
        }
    }

    public void openMintNftModal(String triggerId, String teamId) {
        try {
            String url = "https://slack.com/api/views.open";

            ObjectNode modal = objectMapper.createObjectNode();
            modal.put("trigger_id", triggerId);

            ObjectNode view = modal.putObject("view");
            view.put("type", "modal");
            view.put("callback_id", "mint_nft_modal");

            view.putObject("title").put("type", "plain_text").put("text", "Give Kudos using NFT");
            view.putObject("submit").put("type", "plain_text").put("text", "Mint");
            view.putObject("close").put("type", "plain_text").put("text", "Cancel");

            ArrayNode blocks = view.putArray("blocks");

            // 👥 Employee dropdown
            ObjectNode employeeSelectBlock = blocks.addObject();
            employeeSelectBlock.put("type", "input");
            employeeSelectBlock.put("block_id", "employee_select_block");
            employeeSelectBlock.putObject("label").put("type", "plain_text").put("text", "Select Employee");

            ObjectNode element = employeeSelectBlock.putObject("element");
            element.put("type", "static_select");
            element.put("action_id", "selected_employee");

            ArrayNode options = element.putArray("options");
            List<PTEmployee> employees = walletService.getEmployeeOfPartner(teamId);
            for (PTEmployee e : employees) {
                ObjectNode option = options.addObject();
                option.putObject("text").put("type", "plain_text").put("text", e.getUserName() != null ? e.getUserName() : "Unknown");
                option.put("value", e.getUserId());
            }

            // NFT Name
            ObjectNode nameBlock = blocks.addObject();
            nameBlock.put("type", "input");
            nameBlock.put("block_id", "nft_name_block");
            nameBlock.putObject("label").put("type", "plain_text").put("text", "NFT Name");
            nameBlock.putObject("element").put("type", "plain_text_input").put("action_id", "nft_name");

            // Description
            ObjectNode descBlock = blocks.addObject();
            descBlock.put("type", "input");
            descBlock.put("block_id", "nft_desc_block");
            descBlock.putObject("label").put("type", "plain_text").put("text", "Description");
            descBlock.putObject("element").put("type", "plain_text_input").put("multiline", true).put("action_id", "nft_description");

            // Image upload instructions
            ObjectNode imageTextBlock = blocks.addObject();
            imageTextBlock.put("type", "section");
            imageTextBlock.putObject("text").put("type", "mrkdwn").put("text",
                    ":frame_with_picture: *Upload Image*\nYou can either upload an image file in this thread or click below to open the upload page.");

            // Upload button (opens external upload URL)
            ObjectNode uploadButtonBlock = blocks.addObject();
            uploadButtonBlock.put("type", "actions");
            ArrayNode elements = uploadButtonBlock.putArray("elements");
            ObjectNode uploadButton = elements.addObject();
            uploadButton.put("type", "button");
            uploadButton.put("text", objectMapper.createObjectNode().put("type", "plain_text").put("text", "Upload Image"));
            uploadButton.put("url", "http://localhost:5174/");
            uploadButton.put("action_id", "upload_image_button");

            // Image URL input (optional)
            ObjectNode imgBlock = blocks.addObject();
            imgBlock.put("type", "input");
            imgBlock.put("block_id", "nft_img_block");
            imgBlock.putObject("label").put("type", "plain_text").put("text", "Image URL (copy paste if uploaded manually)");
            imgBlock.putObject("element").put("type", "plain_text_input").put("action_id", "nft_image_url");

            // Log employees for debugging
            employees.forEach(emp -> {
                LOGGER.info("Employee: {} - Wallet: {}", emp.getUserName(),
                        emp.getWallet() != null ? emp.getWallet().getWalletAddress() : "No Wallet");
            });

            // Send request to Slack API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(BOT_TOKEN);

            HttpEntity<String> entity = new HttpEntity<>(modal.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            System.out.println("Mint NFT Modal response: " + response.getBody());
        } catch (Exception e) {
            LOGGER.error("❌ Failed to open Mint NFT Modal", e);
        }
    }

    public void handleMintNftSubmission(JsonNode json) {
        try {
            String teamId = json.path("team").path("id").asText();
            JsonNode values = json.path("view").path("state").path("values");
            String name = values.path("nft_name_block").path("nft_name").path("value").asText();
            String description = values.path("nft_desc_block").path("nft_description").path("value").asText();
            String imageUrl = values.path("nft_img_block").path("nft_image_url").path("value").asText();
            KDPartner partner = kdPartnerRepo.findByTeamId(teamId);
            String collectionAddress = partner.getCollectionAddress();
            if (collectionAddress == null || collectionAddress.isEmpty()) {
                LOGGER.error("No collection address found for team: {}", teamId);
            }
            String employeeId = values.path("employee_select_block").path("selected_employee").path("selected_option").path("value").asText();
            PTEmployee employee = employeeRepo.getPTEmployeeByUserId(employeeId);
            Wallet employeeWallet = walletService.getEmployeeWallet(employeeId);
            String res = mintNftViaNode(name, description, imageUrl, employeeWallet.getPrivateKey(), collectionAddress);
            ObjectNode objRes = (ObjectNode) objectMapper.readTree(res);
            OrganizationNfts nfts = new OrganizationNfts();
            nfts.setMintAddress(objRes.path("nftMint").asText());
            nfts.setMintedByEmployeeId(employeeId);
            nfts.setTeamId(teamId);
            nfts.setCollectionAddress(collectionAddress);
            organizationNftsRepo.save(nfts);
//            sendEphemeralMessage(employeeId,
//                    "🎉Kudos has been given to @" + employee.getUserName() + "Mint Address: " + objRes.path("nftMint").asText());
        } catch (Exception e) {
            LOGGER.error("Failed to handle Mint NFT Submission", e);
        }

    }

    private String mintCollectionNftViaNode(String name, String desc, String imageUrl, String privateKey) {
        String url = "https://nft-metaplex.onrender.com/api/nft/createCollection";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        body.put("description", desc);
        body.put("imageUrl", imageUrl);
        body.put("privateKey", privateKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return res.getBody();
    }

    private String mintNftViaNode(String name, String desc, String imageUrl, String privateKey, String collectionAddress) {
        String url = "https://nft-metaplex.onrender.com/api/nft/createNft";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        body.put("description", desc);
        body.put("imageUrl", imageUrl);
        body.put("secretKey", privateKey);
        body.put("collectionAddress", collectionAddress);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return response.getBody();
    }

    public String uploadToSupabase(MultipartFile file) {
        try {
            String fileName = URLEncoder.encode(file.getOriginalFilename(), StandardCharsets.UTF_8);
            String uploadUrl = String.format("%s/storage/v1/object/%s/%s", SUPABASE_URL, SUPABASE_BUCKET, fileName);
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", SUPABASE_API_KEY);
            headers.set("Authorization", "Bearer " + SUPABASE_API_KEY);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set("x-upsert", "true");
            HttpEntity<byte[]> entity = new HttpEntity<>(file.getBytes(), headers);
            LOGGER.info("Uploading {} to Supabase bucket {}...", fileName, SUPABASE_BUCKET);
            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String publicUrl = String.format("%s/storage/v1/object/public/%s/%s", SUPABASE_URL, SUPABASE_BUCKET, fileName);
                LOGGER.info("✅ Upload successful: {}", publicUrl);
                return publicUrl;
            } else {
                LOGGER.error("❌ Upload failed. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to upload file to Supabase.", e);
        }
        return "";
    }

    // public void sendEphemeralMessage(String userId, String text) {
    // try {
    // String url = "https://slack.com/api/chat.postMessage";
    //
    // ObjectNode body = objectMapper.createObjectNode();
    // body.put("channel", userId);
    // body.put("text", text);
    // body.put("response_type", "in_channel");
    // body.put("as_user", true);
    // restTemplate.postForEntity(url, buildRequest(body), String.class);
    // } catch (Exception e) {
    // LOGGER.error("Failed to send ephemeral message", e);
    // }
    // }

    public void sendResponseMessage(String responseUrl, String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("response_type", "ephemeral"); // or "in_channel" for visible to all
            body.put("text", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            restTemplate.postForEntity(responseUrl, entity, String.class);
            LOGGER.info("✅ Message sent via response_url");
        } catch (Exception e) {
            LOGGER.error("❌ Failed to send message via response_url", e);
        }
    }

    private HttpEntity<String> buildRequest(ObjectNode body) {
        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + BOT_TOKEN);
        headers.set("Content-Type", "application/json");
        return new HttpEntity<>(body.toString(), headers);
    }

    public List<String> getAllNftsOfOrganization(String teamId) {
        List<OrganizationNfts> nfts = organizationNftsRepo.getOrganizationNftsByTeamId((teamId));
        return nfts.stream().map(OrganizationNfts::getMintAddress).toList();
    }

    public static void main(String[] args) {
        Account account = new Account();
        byte[] secret = account.getSecretKey(); // 64 bytes (private + public)
        String base64Secret = Base64.getEncoder().encodeToString(secret);
        System.out.println("Base64 Secret Key (64 bytes): " + base64Secret);
        System.out.println("Public Key: " + account.getPublicKey());
        System.out.println("Length: " + secret.length);
    }

}
