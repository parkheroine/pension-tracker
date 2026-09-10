package com.juyeon.pension_tracker.infra;

import com.juyeon.pension_tracker.domain.change.HoldingChange;
import com.juyeon.pension_tracker.domain.change.HoldingChangeRepository;
import com.juyeon.pension_tracker.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * 보유비율 변동 감지 시 이메일 알림을 발송하는 서비스.
 * 발송 실패해도 배치 중단 없이 로그만 남기고 계속 진행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAlertService {

    private final JavaMailSender mailSender;
    private final HoldingChangeRepository holdingChangeRepository;

    @Value("${alert.email.to}")
    private String recipientEmail;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 지분율 변동 알림 이메일 발송.
     * 발송 실패 시 예외를 삼키고 로그만 남긴다(배치 중단 방지).
     */
    public void sendChangeAlert(Stock stock, HoldingChange change) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject(String.format("[NPS Watch] %s 지분율 변동 감지", stock.getCorpName()));
            message.setText(buildEmailBody(stock, change));

            mailSender.send(message);

            // 알림 발송 성공 → alerted 플래그 업데이트
            change.markAlerted();
            holdingChangeRepository.save(change);

            log.info("이메일 알림 발송 성공: {} ({}) - 변동폭: {}%",
                    stock.getCorpName(), stock.getCorpCode(), change.getChangeAmount());

        } catch (Exception e) {
            log.error("이메일 알림 발송 실패: {} ({}) - {}",
                    stock.getCorpName(), stock.getCorpCode(), e.getMessage());
        }
    }

    private String buildEmailBody(Stock stock, HoldingChange change) {
        return String.format("""
                [NPS Watch] 지분율 변동 알림

                종목명: %s (%s)
                변동 전 보유비율: %s%%
                변동 후 보유비율: %s%%
                변동폭: %s%%
                감지 시각: %s
                """,
                stock.getCorpName(),
                stock.getCorpCode(),
                change.getBeforeStkrt(),
                change.getAfterStkrt(),
                change.getChangeAmount(),
                change.getDetectedAt().format(FORMATTER));
    }
}
