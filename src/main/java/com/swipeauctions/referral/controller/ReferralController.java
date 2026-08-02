package com.swipeauctions.referral.controller;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.referral.entity.Referral;
import com.swipeauctions.referral.repository.ReferralRepository;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Referral capture + a user's own invite stats. Deliberately separate from the registration/auth
 *  flow (UserAuthServiceImpl#register) — capture happens client-side right after the new account
 *  signs in for the first time, as its own best-effort call, so nothing here can ever break signup. */
@RestController
@RequestMapping("/api/referrals")
@RequiredArgsConstructor
@Slf4j
public class ReferralController {

    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    /** The "referral code" is simply the referrer's own user id — the invite link is
     *  {@code /register?ref=<id>}. Best-effort: a bad/self/duplicate referrerId is silently ignored
     *  (still 200) rather than surfaced as an error, since by the time the frontend calls this the
     *  user's account already exists and is signed in — nothing should look "broken" over an invite
     *  link edge case. */
    @PostMapping("/capture")
    public CaptureResponse capture(@Valid @RequestBody CaptureRequest req) {
        User referred = loggedInUserUtil.getCurrentUser();

        if (req.referrerId().equals(referred.getId())) {
            return new CaptureResponse(false, "Can't refer yourself.");
        }
        if (referralRepository.existsByReferred_Id(referred.getId())) {
            return new CaptureResponse(false, "Referral already recorded for this account.");
        }
        User referrer = userRepository.findById(req.referrerId()).orElse(null);
        if (referrer == null) {
            log.info("[referral] capture skipped — unknown referrer id {}", req.referrerId());
            return new CaptureResponse(false, "Invite link not recognized.");
        }

        referralRepository.save(Referral.builder().referrer(referrer).referred(referred).build());
        return new CaptureResponse(true, "Referral recorded.");
    }

    /** The signed-in user's own invite link + how many people have signed up through it. */
    @GetMapping("/mine")
    public MyReferralsResponse mine() {
        User user = loggedInUserUtil.getCurrentUser();
        int count = referralRepository.findByReferrer_Id(user.getId()).size();
        return new MyReferralsResponse(user.getId(), count);
    }

    public record CaptureRequest(@NotNull UUID referrerId) {}

    public record CaptureResponse(boolean recorded, String message) {}

    public record MyReferralsResponse(UUID referralCode, int totalReferred) {}
}
