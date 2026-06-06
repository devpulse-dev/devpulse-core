package ru.x5.devpulse.domain.model.performance;

/**
 * Баланс работы за период: дефекты (firefighting) vs разработка+задачи (building).
 * Доли в [0..1]; при отсутствии карточек обе = 0.
 */
public record WorkBalance(int defectCount, int buildCount, double defectShare, double buildShare) {

    public static final WorkBalance EMPTY = new WorkBalance(0, 0, 0.0, 0.0);

    public static WorkBalance of(int defectCount, int buildCount) {
        int total = defectCount + buildCount;
        if (total == 0) {
            return EMPTY;
        }
        double defectShare = (double) defectCount / total;
        return new WorkBalance(defectCount, buildCount, defectShare, 1.0 - defectShare);
    }
}
