import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

// ── Знаки гри ─────────────────────────────────────────────────────────────
enum Sign {
    WELL("Криниця", "【 】"), SCISSORS("Ножиці", "✂"), PAPER("Папір", "📄");

    final String name, emoji;
    Sign(String name, String emoji) { this.name = name; this.emoji = emoji; }

    int versus(Sign other) {
        if (this == other) return 0;
        if ((this == WELL     && other == SCISSORS) ||
                (this == SCISSORS && other == PAPER)    ||
                (this == PAPER    && other == WELL)) return 1;
        return -1;
    }
}

// ── Режими комп'ютера ──────────────────────────────────────────────────────
enum Mode {
    RANDOM       ("1 — Випадкові ходи"),
    SESSION_STATS("2 — Аналіз поточного сеансу"),
    HISTORY_STATS("3 — Аналіз попередніх сеансів (з файлу)");

    final String label;
    Mode(String label) { this.label = label; }

    @Override public String toString() { return label; }
}

// ── Результат сеансу ───────────────────────────────────────────────────────
class SessionResult {
    final int sessionNumber;
    final Mode mode;
    int userWins, computerWins, draws;

    SessionResult(int n, Mode m) { sessionNumber = n; mode = m; }

    String getOutcome() {
        if (userWins > computerWins) return "Перемога користувача";
        if (computerWins > userWins) return "Перемога комп'ютера";
        return "Нічия";
    }
    int getTotalRounds() { return userWins + computerWins + draws; }

    @Override public String toString() {
        return String.format(
                "Сеанс %d | %s | Раундів: %d | Ви: %d | Комп: %d | Нічиїх: %d | %s",
                sessionNumber, mode.label, getTotalRounds(),
                userWins, computerWins, draws, getOutcome());
    }
    String toFileString() {
        return sessionNumber + "," + mode.ordinal() + "," + userWins + "," + computerWins + "," + draws;
    }
}

// ── Файловий менеджер ──────────────────────────────────────────────────────
class FileManager {
    private static final String RESULTS_FILE = "data/results.csv";
    private static final String HISTORY_FILE = "data/user_history.csv";

    static void saveResult(SessionResult r) {
        ensureDir();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(RESULTS_FILE, true))) {
            w.write(r.toFileString()); w.newLine();
        } catch (IOException e) { e.printStackTrace(); }
    }

    static List<SessionResult> loadResults() {
        List<SessionResult> list = new ArrayList<>();
        Path p = Path.of(RESULTS_FILE);
        if (!Files.exists(p)) return list;
        try (BufferedReader r = Files.newBufferedReader(p)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.trim().split(",");
                if (parts.length < 5) continue;
                try {
                    SessionResult sr = new SessionResult(
                            Integer.parseInt(parts[0]), Mode.values()[Integer.parseInt(parts[1])]);
                    sr.userWins     = Integer.parseInt(parts[2]);
                    sr.computerWins = Integer.parseInt(parts[3]);
                    sr.draws        = Integer.parseInt(parts[4]);
                    list.add(sr);
                } catch (Exception ignored) {}
            }
        } catch (IOException e) { e.printStackTrace(); }
        return list;
    }

    static void saveUserMoveHistory(Map<Sign, Integer> freq) {
        ensureDir();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(HISTORY_FILE, true))) {
            w.write(freq.getOrDefault(Sign.WELL, 0) + "," +
                    freq.getOrDefault(Sign.SCISSORS, 0) + "," +
                    freq.getOrDefault(Sign.PAPER, 0));
            w.newLine();
        } catch (IOException e) { e.printStackTrace(); }
    }

    static Map<Sign, Integer> loadUserMoveHistory() {
        Map<Sign, Integer> freq = new EnumMap<>(Sign.class);
        for (Sign s : Sign.values()) freq.put(s, 0);
        Path p = Path.of(HISTORY_FILE);
        if (!Files.exists(p)) return freq;
        try (BufferedReader r = Files.newBufferedReader(p)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.trim().split(",");
                if (parts.length < 3) continue;
                try {
                    freq.merge(Sign.WELL,     Integer.parseInt(parts[0]), Integer::sum);
                    freq.merge(Sign.SCISSORS, Integer.parseInt(parts[1]), Integer::sum);
                    freq.merge(Sign.PAPER,    Integer.parseInt(parts[2]), Integer::sum);
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) { e.printStackTrace(); }
        return freq;
    }

    static String buildStatReport() {
        List<SessionResult> all = loadResults();
        if (all.isEmpty()) return "Статистика відсутня. Зіграйте хоча б один сеанс.";

        int totalSessions = all.size(), totalUserWins = 0, totalCompWins = 0,
                totalDraws = 0, totalRounds = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("                   СТАТИСТИКА ІГОР\n");
        sb.append("═══════════════════════════════════════════════════════\n\n");
        sb.append("Деталі по сеансах:\n");
        sb.append("─".repeat(55)).append("\n");

        for (SessionResult r : all) {
            sb.append(r).append("\n");
            totalUserWins += r.userWins;
            totalCompWins += r.computerWins;
            totalDraws    += r.draws;
            totalRounds   += r.getTotalRounds();
        }

        long userSessionWins = all.stream().filter(r -> r.userWins > r.computerWins).count();
        long compSessionWins = all.stream().filter(r -> r.computerWins > r.userWins).count();

        sb.append("─".repeat(55)).append("\n\n");
        sb.append("ЗАГАЛЬНА СТАТИСТИКА:\n");
        sb.append(String.format("  Всього сеансів:             %d%n", totalSessions));
        sb.append(String.format("  Всього раундів:             %d%n", totalRounds));
        sb.append(String.format("  Перемоги користувача:       %d%n", totalUserWins));
        sb.append(String.format("  Перемоги комп'ютера:        %d%n", totalCompWins));
        sb.append(String.format("  Нічиїх (повторів):          %d%n%n", totalDraws));
        sb.append(String.format("  Сеансів виграно вами:       %d із %d (%.1f%%)%n",
                userSessionWins, totalSessions, 100.0 * userSessionWins / totalSessions));
        sb.append(String.format("  Сеансів виграно комп'ютером: %d із %d (%.1f%%)%n",
                compSessionWins, totalSessions, 100.0 * compSessionWins / totalSessions));
        return sb.toString();
    }

    private static void ensureDir() {
        File dir = new File("data");
        if (!dir.exists()) dir.mkdirs();
    }
}

// ── Головний клас / GUI ────────────────────────────────────────────────────
public class RpsGame extends JFrame {

    private static final String SCENE_SETUP = "SETUP";
    private static final String SCENE_GAME  = "GAME";
    private static final String SCENE_STATS = "STATS";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel     mainPanel  = new JPanel(cardLayout);
    private final Random     random     = new Random();

    // Стан гри
    private Mode mode;
    private int  totalSessions, currentSession;
    private int  sessionWins, sessionLosses, sessionDraws;   // рахунок поточного сеансу
    private int  totalWins,   totalLosses,   totalDraws;     // рахунок усієї гри (всіх сеансів)

    private final Map<Sign, Integer> sessionFreq = new EnumMap<>(Sign.class);
    private       Map<Sign, Integer> historyFreq = new EnumMap<>(Sign.class);

    // Віджети
    private JLabel    lblSession; // "Сеанс 2 / 5"
    private JLabel    lblScore;   // накопичений рахунок усієї гри
    private JLabel    lblPlayerSign, lblComputerSign, lblResult;
    private JButton   btnWell, btnScissors, btnPaper;
    private JTextArea statsArea;

    public RpsGame() {
        super("Криниця · Ножиці · Папір");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        for (Sign s : Sign.values()) { sessionFreq.put(s, 0); historyFreq.put(s, 0); }

        mainPanel.add(buildSetupScene(), SCENE_SETUP);
        mainPanel.add(buildGameScene(),  SCENE_GAME);
        mainPanel.add(buildStatsScene(), SCENE_STATS);
        add(mainPanel);
        cardLayout.show(mainPanel, SCENE_SETUP);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── Екран налаштувань ──────────────────────────────────────────────────
    private JPanel buildSetupScene() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(500, 320));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(10, 16, 10, 16);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        panel.add(emojiLbl("Криниця  Ножиці  Папір", 22), g);

        g.gridy = 1; g.gridwidth = 1; g.gridx = 0;
        panel.add(lbl("Кількість сеансів:", 14, false), g);
        JSpinner spinSessions = new JSpinner(new SpinnerNumberModel(3, 1, 99, 1));
        spinSessions.setFont(new Font("Arial", Font.PLAIN, 14));
        g.gridx = 1; panel.add(spinSessions, g);

        g.gridy = 2; g.gridx = 0;
        panel.add(lbl("Режим комп'ютера:", 14, false), g);
        JComboBox<Mode> comboMode = new JComboBox<>(Mode.values());
        comboMode.setFont(new Font("Arial", Font.PLAIN, 13));
        g.gridx = 1; panel.add(comboMode, g);

        JButton btnStart = btn("Почати гру");
        g.gridy = 3; g.gridx = 0; g.gridwidth = 2; panel.add(btnStart, g);

        JButton btnStats = btn("Переглянути статистику");
        g.gridy = 4; panel.add(btnStats, g);

        btnStart.addActionListener(e -> {
            totalSessions = (int) spinSessions.getValue();
            mode = (Mode) comboMode.getSelectedItem();
            currentSession = 0;
            totalWins = totalLosses = totalDraws = 0;
            if (mode == Mode.HISTORY_STATS)
                historyFreq = FileManager.loadUserMoveHistory();
            startNextSession();
        });
        btnStats.addActionListener(e -> {
            statsArea.setText(FileManager.buildStatReport());
            cardLayout.show(mainPanel, SCENE_STATS);
        });
        return panel;
    }

    // ── Ігровий екран ──────────────────────────────────────────────────────
    private JPanel buildGameScene() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Верхня панель: сеанс + рахунок
        JPanel topBar = new JPanel(new GridLayout(1, 2));
        topBar.setOpaque(false);

        lblSession = lbl("Сеанс 1 / 1", 13, false);
        lblScore   = lbl("Виграш: 0  |  Програш: 0  |  Нічия: 0", 13, false);
        lblScore.setHorizontalAlignment(SwingConstants.RIGHT);

        topBar.add(lblSession);
        topBar.add(lblScore);
        panel.add(topBar, BorderLayout.NORTH);

        // ── Центр: іконки знаків ──
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 20, 6, 20);

        lblPlayerSign   = emojiLbl("—", 56);
        lblComputerSign = emojiLbl("?", 56);
        lblResult       = lbl("Оберіть знак", 18, true);

        g.gridy = 0; g.gridx = 0; center.add(lbl("ВИ",       12, false), g);
        g.gridx = 1;              center.add(lbl("VS",        20, true),  g);
        g.gridx = 2;              center.add(lbl("КОМ'ЮТЕР", 12, false), g);
        g.gridy = 1; g.gridx = 0; center.add(lblPlayerSign,   g);
        g.gridx = 2;              center.add(lblComputerSign,  g);
        g.gridy = 2; g.gridx = 0; g.gridwidth = 3; center.add(lblResult, g);
        panel.add(center, BorderLayout.CENTER);

        // ── Кнопки вибору знака ──
        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        btnPanel.setOpaque(false);
        btnWell     = btn("Криниця");
        btnScissors = btn("Ножиці");
        btnPaper    = btn("Папір");
        btnWell    .addActionListener(e -> playRound(Sign.WELL));
        btnScissors.addActionListener(e -> playRound(Sign.SCISSORS));
        btnPaper   .addActionListener(e -> playRound(Sign.PAPER));
        btnPanel.add(btnWell); btnPanel.add(btnScissors); btnPanel.add(btnPaper);
        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ── Екран статистики ───────────────────────────────────────────────────
    private JPanel buildStatsScene() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));
        panel.setPreferredSize(new Dimension(560, 400));

        statsArea = new JTextArea();
        statsArea.setFont(new Font("Courier New", Font.PLAIN, 13));
        statsArea.setEditable(false);
        statsArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(new JScrollPane(statsArea), BorderLayout.CENTER);

        JButton btnBack = btn("Назад до меню");
        btnBack.addActionListener(e -> cardLayout.show(mainPanel, SCENE_SETUP));
        panel.add(btnBack, BorderLayout.SOUTH);
        return panel;
    }

    // ── Логіка гри ─────────────────────────────────────────────────────────
    private void startNextSession() {
        currentSession++;
        sessionWins = sessionLosses = sessionDraws = 0;
        for (Sign s : Sign.values()) sessionFreq.put(s, 0);
        updateLabels();
        setButtonsEnabled(true);
        lblPlayerSign.setText("—");
        lblComputerSign.setText("?");
        lblResult.setText("Сеанс " + currentSession + " — оберіть знак");
        cardLayout.show(mainPanel, SCENE_GAME);
    }

    private void playRound(Sign playerSign) {
        setButtonsEnabled(false);

        // Комп'ютер робить хід ДО запису ходу гравця
        Sign compSign = computerMove();
        sessionFreq.merge(playerSign, 1, Integer::sum);

        lblPlayerSign.setText(playerSign.emoji);
        lblComputerSign.setText(compSign.emoji);

        int outcome = playerSign.versus(compSign);
        boolean sessionDone;

        if (outcome == 1) {
            sessionWins++;
            totalWins++;
            lblResult.setText("Ви перемогли раунд!");
            sessionDone = true;
        } else if (outcome == -1) {
            sessionLosses++;
            totalLosses++;
            lblResult.setText("Комп'ютер переміг раунд!");
            sessionDone = true;
        } else {
            sessionDraws++;
            totalDraws++;
            lblResult.setText("Нічия — граємо ще раз!");
            sessionDone = false;
        }

        // Оновлюємо обидва лічильники одразу після кожного раунду
        updateLabels();

        if (sessionDone) {
            SessionResult result = new SessionResult(currentSession, mode);
            result.userWins     = sessionWins;
            result.computerWins = sessionLosses;
            result.draws        = sessionDraws;
            FileManager.saveResult(result);
            FileManager.saveUserMoveHistory(new EnumMap<>(sessionFreq));
            if (mode == Mode.HISTORY_STATS)
                historyFreq = FileManager.loadUserMoveHistory();

            javax.swing.Timer delay = new javax.swing.Timer(1300, e -> {
                if (currentSession < totalSessions) startNextSession();
                else showFinalResults();
            });
            delay.setRepeats(false);
            delay.start();
        } else {
            javax.swing.Timer delay = new javax.swing.Timer(900, e -> setButtonsEnabled(true));
            delay.setRepeats(false);
            delay.start();
        }
    }

    private void showFinalResults() {
        String msg = String.format(
                "Гру завершено!\n\nРезультати (%d сеансів):\n  Перемоги : %d\n  Поразки  : %d\n  Нічиї    : %d",
                totalSessions, totalWins, totalLosses, totalDraws);
        int opt = JOptionPane.showConfirmDialog(this, msg,
                "Гру завершено", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null);
        if (opt == JOptionPane.YES_OPTION) cardLayout.show(mainPanel, SCENE_SETUP);
        else { statsArea.setText(FileManager.buildStatReport()); cardLayout.show(mainPanel, SCENE_STATS); }
    }

    // ── ШІ комп'ютера ──────────────────────────────────────────────────────
    private Sign computerMove() {
        return switch (mode) {
            case RANDOM        -> randomSign();
            case SESSION_STATS -> smartMove(sessionFreq);
            case HISTORY_STATS -> smartMove(historyFreq);
        };
    }

    private Sign randomSign() {
        Sign[] v = Sign.values(); return v[random.nextInt(v.length)];
    }

    private Sign smartMove(Map<Sign, Integer> freq) {
        int total = freq.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return randomSign();
        Sign top = Collections.max(freq.entrySet(), Map.Entry.comparingByValue()).getKey();
        return switch (top) {
            case WELL     -> Sign.PAPER;
            case SCISSORS -> Sign.WELL;
            case PAPER    -> Sign.SCISSORS;
        };
    }

    // ── Оновлення лічильників ──────────────────────────────────────────────
    private void updateLabels() {
        lblSession.setText("Сеанс " + currentSession + " / " + totalSessions);
        lblScore.setText(String.format(
                "Виграш: %d  |  Програш: %d  |  Нічия: %d",
                totalWins, totalLosses, totalDraws));
    }

    private void setButtonsEnabled(boolean e) {
        btnWell.setEnabled(e); btnScissors.setEnabled(e); btnPaper.setEnabled(e);
    }

    private static JLabel lbl(String text, int size, boolean bold) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Arial", bold ? Font.BOLD : Font.PLAIN, size));
        return l;
    }

    private static JLabel emojiLbl(String text, int size) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Dialog", Font.PLAIN, size));
        return l;
    }

    private static JButton btn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Dialog", Font.BOLD, 14));
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(10, 20, 10, 20));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RpsGame::new);
    }
}