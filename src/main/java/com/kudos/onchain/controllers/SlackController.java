package com.kudos.onchain.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kudos.onchain.services.SlackService;
import com.kudos.onchain.services.WalletService;
import com.kudos.onchain.utils.ThreadPollUtil;
import jakarta.persistence.GeneratedValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/slack")
@CrossOrigin(origins = "*")
public class SlackController {

    @Autowired
    private SlackService slackService;
    @Autowired
    private WalletService walletService;

    @GetMapping("/install")
    public RedirectView installApp() {
        return slackService.installApp();
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<?> callback(@RequestParam String code) {
        ObjectNode res = slackService.exchangeCodeToAccessToken(code);
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @RequestMapping("/events")
    public ResponseEntity<String> handleEvent(@RequestBody ObjectNode payload) {
        String res = slackService.handleSlackEvents(payload);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @RequestMapping("/interactions")
    public ResponseEntity<String> handleInteractions(@RequestParam(value = "payload", required = false) String payload) {
        ExecutorService es = ThreadPollUtil.getExecutor();
        es.submit(() -> {
            try {
                slackService.handleInteractions(payload);
            } catch (Exception e) {
            }
        });
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PostMapping("/upload-image")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        return slackService.uploadToSupabase(file);
    }
    
    @GetMapping("/get-all-nfts-of-organization")
    public ResponseEntity< ? > getAllNftsOfOrganization(@RequestParam String teamId) {
        List<String> res = slackService.getAllNftsOfOrganization(teamId);
        return new ResponseEntity<>(res, HttpStatus.OK);
    }
    
    @GetMapping("/get-wallet-balance")
    public ResponseEntity< ? > getWalletBalance(@RequestParam String walletAddress) {
        String balance = walletService.getWalletBalance(walletAddress);
        return new ResponseEntity<>(balance, HttpStatus.OK);
    }

}
