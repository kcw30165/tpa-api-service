package com.bct.ngtpa.apiservice.adapter.out.captcha;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.GeneratedCaptcha;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaImageGeneratorPort;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.imageio.ImageIO;
import reactor.core.publisher.Mono;

public final class JavaCaptchaImageGeneratorAdapter implements CaptchaImageGeneratorPort {

    private static final String MIME_TYPE = "image/png";
    private static final int WIDTH_PER_CHARACTER = 24;
    private static final int IMAGE_HEIGHT = 72;

    private final SecureRandomCaptchaTextGenerator textGenerator;
    private final SecureRandom secureRandom;

    public JavaCaptchaImageGeneratorAdapter() {
        this(new SecureRandomCaptchaTextGenerator(), new SecureRandom());
    }

    public JavaCaptchaImageGeneratorAdapter(SecureRandomCaptchaTextGenerator textGenerator, SecureRandom secureRandom) {
        this.textGenerator = Objects.requireNonNull(textGenerator, "textGenerator must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    @Override
    public Mono<GeneratedCaptcha> generate(CaptchaPolicy policy) {
        return Mono.fromSupplier(() -> {
            String answer = textGenerator.generate(policy);
            return new GeneratedCaptcha(answer, render(answer));
        });
    }

    private CaptchaImage render(String answer) {
        int width = Math.max(160, answer.length() * WIDTH_PER_CHARACTER);
        BufferedImage image = new BufferedImage(width, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(randomPastel());
            graphics.fillRect(0, 0, width, IMAGE_HEIGHT);

            drawNoise(graphics, width);

            graphics.setFont(new Font(pickFontName(), Font.BOLD, 36 + secureRandom.nextInt(5)));
            int baseline = 44 + secureRandom.nextInt(12);
            int x = 12;
            for (int index = 0; index < answer.length(); index++) {
                graphics.setColor(randomDark());
                graphics.drawString(String.valueOf(answer.charAt(index)), x, baseline + secureRandom.nextInt(7) - 3);
                x += 18 + secureRandom.nextInt(10);
            }

            return new CaptchaImage(encodePng(image), MIME_TYPE);
        } finally {
            graphics.dispose();
        }
    }

    private void drawNoise(Graphics2D graphics, int width) {
        for (int index = 0; index < 6; index++) {
            graphics.setColor(randomDark());
            graphics.drawLine(
                    secureRandom.nextInt(width),
                    secureRandom.nextInt(IMAGE_HEIGHT),
                    secureRandom.nextInt(width),
                    secureRandom.nextInt(IMAGE_HEIGHT));
        }
        for (int index = 0; index < 40; index++) {
            graphics.setColor(randomDark());
            int x = secureRandom.nextInt(width);
            int y = secureRandom.nextInt(IMAGE_HEIGHT);
            graphics.fillRect(x, y, 2, 2);
        }
    }

    private String encodePng(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to render captcha image", exception);
        }
    }

    private Color randomPastel() {
        return new Color(190 + secureRandom.nextInt(50), 190 + secureRandom.nextInt(50), 190 + secureRandom.nextInt(50));
    }

    private Color randomDark() {
        return new Color(secureRandom.nextInt(120), secureRandom.nextInt(120), secureRandom.nextInt(120));
    }

    private String pickFontName() {
        return switch (secureRandom.nextInt(3)) {
            case 0 -> "SansSerif";
            case 1 -> "Dialog";
            default -> "Monospaced";
        };
    }
}