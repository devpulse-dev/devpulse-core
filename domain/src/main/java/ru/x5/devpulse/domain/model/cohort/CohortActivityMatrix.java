package ru.x5.devpulse.domain.model.cohort;

import java.time.YearMonth;
import java.util.List;

/**
 * Матрица активности «разработчик × месяц».
 *
 * @param months      ось колонок (contiguous, по возрастанию)
 * @param developers  строки; {@code cells} каждой выровнены по {@code months}
 */
public record CohortActivityMatrix(List<YearMonth> months, List<DeveloperActivity> developers) {}
