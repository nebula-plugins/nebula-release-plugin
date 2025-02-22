package nebula.plugin.release.git.opinion

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum TimestampPrecision {
    DAYS(createDateTimeFormatter("yyyyMMdd")),
    HOURS(createDateTimeFormatter("yyyyMMddHH")),
    MINUTES(createDateTimeFormatter("yyyyMMddHHmm")),
    SECONDS(createDateTimeFormatter("yyyyMMddHHmmss")),
    MILLISECONDS(createDateTimeFormatter("YYYYMMddHHmmssSSS"))

    final DateTimeFormatter dateTimeFormatter

    private TimestampPrecision(DateTimeFormatter formatter) {
        this.dateTimeFormatter = formatter
    }

    static createDateTimeFormatter(String format) {
        return DateTimeFormatter
                .ofPattern(format)
                .withZone(ZoneOffset.UTC)
    }

    static TimestampPrecision from(String precision) {
        try {
            return valueOf(precision.toUpperCase())
        } catch (Exception e) {
            return MINUTES
        }
    }
}