package ee.revkassa.service;

import ee.revkassa.controller.me.dto.AccountEntryDto;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.infrastructure.mail.MailMessage;
import ee.revkassa.infrastructure.validation.Iban;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The text of one invoice email, in Estonian with Estonian formats ("27,40 €", "30.10.2026"). The balance lines start
 * from the opening balance and end at the closing balance, which is the debt, so the lines always add up.
 */
public record InvoiceEmail(String to, String memberName, String number, long amountCents, LocalDate dueDate,
                           String payeeName, String iban, OffsetDateTime periodStart, long openingBalanceCents,
                           List<AccountEntryDto> entries, OffsetDateTime periodEnd) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String LINE_FORMAT = "%-10s  %-40s %12s";
    private static final char MINUS = '−';
    private static final char THOUSANDS_SEPARATOR = ' ';

    public MailMessage toMailMessage() {
        return new MailMessage(to, subject(), body());
    }

    public String subject() {
        return "Arve " + number + " · " + payeeName;
    }

    public String body() {
        return """
                Tere, %s!

                Sinu RevKassa saldo on miinuses. Palun tasu võlg hiljemalt %s.

                Arve number:        %s
                Tasumisele kuulub:  %s
                Maksetähtaeg:       %s

                Makseandmed
                Saaja:     %s
                IBAN:      %s
                Selgitus:  Arve %s
                Summa:     %s

                Saldo muutused
                %s

                Kui oled juba maksnud, teata maksest RevKassas (Konto → Teata maksest) ja admin kinnitab selle.
                See arve on maksenõue, mitte käibemaksuarve.

                Tervitades
                %s
                """.formatted(memberName, formatDate(dueDate), number, formatEuro(amountCents), formatDate(dueDate),
                payeeName, Iban.group(iban), number, formatEuro(amountCents), balanceLines(), payeeName);
    }

    private String balanceLines() {
        StringBuilder lines = new StringBuilder(line(periodStart, "Algsaldo", openingBalanceCents));
        for (AccountEntryDto entry : entries) {
            lines.append('\n').append(line(entry.getCreatedAt(), entry.getDescription(), entry.getAmountCents()));
        }
        return lines.append('\n').append(line(periodEnd, "Lõppsaldo", -amountCents)).toString();
    }

    private static String line(OffsetDateTime at, String description, long amountCents) {
        return LINE_FORMAT.formatted(formatDate(at.atZoneSameInstant(ClockConfig.APP_ZONE).toLocalDate()),
                description, formatEuro(amountCents)).stripTrailing();
    }

    private static String formatDate(LocalDate date) {
        return DATE.format(date);
    }

    // "−1 234,50 €" as et-EE writes it: minus sign, non-breaking space between thousands, decimal comma
    static String formatEuro(long cents) {
        long absolute = Math.abs(cents);
        StringBuilder euros = new StringBuilder(Long.toString(absolute / 100));
        for (int i = euros.length() - 3; i > 0; i -= 3) {
            euros.insert(i, THOUSANDS_SEPARATOR);
        }
        String sign = cents < 0 ? String.valueOf(MINUS) : "";
        return sign + euros + "," + "%02d".formatted(absolute % 100) + " €";
    }
}
