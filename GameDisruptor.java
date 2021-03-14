package tests;

import ai.abstraction.LightRush;
import ai.core.AI;
import ai.abstraction.pathfinding.NewStarPathFinding;
import gui.PhysicalGameStatePanel;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jdom.*;

import org.jdom.input.SAXBuilder;
import rts.GameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

import static rts.units.UnitTypeTable.*;


public class GameDisruptor extends JPanel {
    // The constants used for the red teams stats
    private static final int HP = 20;
    private static final int DMG = 10;
    private static final int RNG = 2;
    private static final int MT = 8;
    private static final int AT = 6;

    // The evolved blue team unit stats
    private static int BlueHP = HP;
    private static int BlueDamage = DMG;
    private static int BlueRange = RNG;
    private static int BlueMoveTime = MT;
    private static int BlueAttackTime = AT;

    // The static red teams unit stats
    private static int RedHP = HP;
    private static int RedDamage = DMG;
    private static int RedRange = RNG;
    private static int RedMoveTime = MT;
    private static int RedAttackTime = AT;

    // The evolution parameter defaults
    private static int Generations = 100;
    private static int Population = 30;
    private static float MutationRate = 0.1f;
    private static float CrossoverRate = 0.9f;

    // The number of simulations to perform per solution
    private static int SimulationCount = 10000;

    // Keep track of if the simulation is running
    private static boolean Running = false;

    // The GUI Labels that need to be updated after simulations
    private static JLabel BlueWins;
    private static JLabel RedWins;
    private static JLabel Draws;
    private static JLabel BlueWinRate;
    private static JLabel AverageHPDifference;
    private static JLabel StdDevHPDifference;

    // The GUI entry fields that are updated
    private static JTextField HPEntry;
    private static JTextField DMGEntry;
    private static JTextField RNGEntry;
    private static JTextField MTEntry;
    private static JTextField ATEntry;
    private static JTextField SeedEntry;

    // The win-rate of a solution and the default target win-rate
    private static float WinRate;
    private static float TargetWinRate = 100f;

    // The fitness function weighed sum weightings
    private static float WinWeight = 0.75f;
    private static float ChangeWeight = 0.25f;
    private static float HpWeight = 0.0f;

    // The seed used for the evolution experiment
    private static String Seed = "";

    // Track the number of Blue wins/Red wins/Draws for a given solution
    private static int CurrentDraws = 0;
    private static int CurrentBWins = 0;
    private static int CurrentRWins = 0;

    // Track the average hit point difference and standard deviation
    private static float CurrentAverage = 0;
    private static float CurrentStdDev = 0;

    // Store a list of battle results
    private static CopyOnWriteArrayList<Integer> CurrentResults = new CopyOnWriteArrayList<Integer>();

    // UI Model to allow adding and removing game variables to be analysed
    private static DefaultListModel ListModel = new DefaultListModel();
    private static JButton AddItem;
    private static JButton RemoveItem;

    // The label for the current status of the prototype
    private static JLabel Status;

    // The map xml document to be used when evaluating a solution
    public static Element MapXML;

    // Simple interface to listen to text field input events
    @FunctionalInterface
    public interface SL extends DocumentListener {
        void update(DocumentEvent e);

        @Override
        default void insertUpdate(DocumentEvent e) {
            try {
                update(e);
            } catch(Exception e1){
            }
        }
        @Override
        default void removeUpdate(DocumentEvent e) {
            try {
                update(e);
            } catch(Exception e1){
            }
        }
        @Override
        default void changedUpdate(DocumentEvent e) {
            try {
                update(e);
            } catch(Exception e1){
            }
        }
    }

    // The run simulation function performs the evaluation of a solution and can run across multiple threads or show the result of a single simulation
    public static void RunSimulation(boolean display, int threadsToUse) throws Exception {
        UnitTypeTable utt = new UnitTypeTable(VERSION_ORIGINAL, MOVE_CONFLICT_RESOLUTION_CANCEL_BOTH);

        // Define a custom blue unit using the game variables defined in the chromosome
        UnitType blight = new UnitType();
        blight.name = "BlueLight";
        blight.cost = 2;
        blight.hp = BlueHP;
        blight.minDamage = 0;
        blight.maxDamage = BlueDamage;
        blight.attackRange = BlueRange;
        blight.produceTime = 80;
        blight.moveTime = BlueMoveTime;
        blight.attackTime = BlueAttackTime;
        blight.isResource = false;
        blight.isStockpile = false;
        blight.canHarvest = false;
        blight.canMove = true;
        blight.canAttack = true;
        blight.sightRadius = 2;
        utt.addUnitType(blight);

        // Define a custom red unit using the static game variables defined
        UnitType rlight = new UnitType();
        rlight.name = "RedLight";
        rlight.cost = 2;
        rlight.hp = RedHP;
        rlight.minDamage = 0;
        rlight.maxDamage = RedDamage;
        rlight.attackRange = RedRange;
        rlight.produceTime = 80;
        rlight.moveTime = RedMoveTime;
        rlight.attackTime = RedAttackTime;
        rlight.isResource = false;
        rlight.isStockpile = false;
        rlight.canHarvest = false;
        rlight.canMove = true;
        rlight.canAttack = true;
        rlight.sightRadius = 2;
        utt.addUnitType(rlight);

        CurrentBWins = 0;
        CurrentRWins = 0;
        CurrentDraws = 0;

        if(display){
            // Setup game conditions
            PhysicalGameState pgs = PhysicalGameState.fromXML(MapXML, utt);

            //Update unit health to match stats
            for(Unit unit : pgs.getUnits()){
                if(unit.getType() == blight){
                    unit.setHitPoints(blight.hp);
                } else if(unit.getType() == rlight){
                    unit.setHitPoints(rlight.hp);
                }
            }
            GameState gs = new GameState(pgs, utt);

            int MAXCYCLES = 350; // Maximum game length
            int PERIOD = 25; // Refresh rate for display (milliseconds)

            boolean gameover = false;

            // Set the AIs
            AI ai1 = new LightRush(utt, new NewStarPathFinding());
            AI ai2 = new LightRush(utt, new NewStarPathFinding());

            // Show the playback GUI
            JFrame w = PhysicalGameStatePanel.newVisualizer(gs, 768, 768, false, PhysicalGameStatePanel.COLORSCHEME_BLACK);

            Thread.sleep(1000);
            long nextTimeToUpdate = System.currentTimeMillis() + PERIOD;

            do {
                if (System.currentTimeMillis() >= nextTimeToUpdate) {
                    PlayerAction pa1 = ai1.getAction(0, gs);
                    PlayerAction pa2 = ai2.getAction(1, gs);

                    gs.issueSafe(pa1);
                    gs.issueSafe(pa2);

                    // simulate:
                    gameover = gs.cycle();
                    w.repaint();
                    nextTimeToUpdate += PERIOD;
                } else {
                    try {
                        Thread.sleep(1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if(w.isVisible() == false) {
                    gameover = true;
                    Running = false;
                }
            } while (!gameover && gs.getTime() < MAXCYCLES);
            w.dispose();
        } else if(threadsToUse == 1){ // If only one thread is selected no need to split work up across threads
            int runs = SimulationCount;
            int bWins = 0;
            int rWins = 0;
            int draws = 0;
            ArrayList<Integer> matchResults = new ArrayList();

            // Perform a series of simulations based on the number defined in simulation count
            for(int i = 0; i < runs; i++) {
                // Setup game conditions
                PhysicalGameState pgs = PhysicalGameState.fromXML(MapXML, utt);
                //PhysicalGameState pgs = PhysicalGameState.load(map, utt);

                ArrayList<Unit> units = new ArrayList<>();

                //Update unit health to match stats
                for (Unit unit : pgs.getUnits()) {
                    if (unit.getType() == blight) {
                        unit.setHitPoints(blight.hp);
                        units.add(unit);
                    } else if (unit.getType() == rlight) {
                        unit.setHitPoints(rlight.hp);
                        units.add(unit);
                    }
                }
                GameState gs = new GameState(pgs, utt);

                int MAXCYCLES = 350; // Maximum game length
                int PERIOD = 25; // Refresh rate for display (milliseconds)

                boolean gameover = false;

                // Set the AIs
                AI ai1 = new LightRush(utt, new NewStarPathFinding());
                AI ai2 = new LightRush(utt, new NewStarPathFinding());

                do {
                    PlayerAction pa1 = ai1.getAction(0, gs);
                    PlayerAction pa2 = ai2.getAction(1, gs);
                    gs.issueSafe(pa1);
                    gs.issueSafe(pa2);

                    // simulate:
                    gameover = gs.cycle();
                } while (!gameover && gs.getTime() < MAXCYCLES);

                // Track the hit points of each teams units
                int blueHP = 0;
                int redHP = 0;

                // Calcuclate the hit points remaing for each of the units
                for(Unit u : units){
                    if (u.getType() == blight) {
                        blueHP += u.getHitPoints();
                    } else if (u.getType() == rlight) {
                        redHP += u.getHitPoints();
                    }
                }

                ai1.gameOver(gs.winner()); // TODO what do these do?
                ai2.gameOver(gs.winner());

                int result = gs.winner();
                if (result == -1) {
                    draws++;
                } else if (result == 0) {
                    bWins++;
                } else if (result == 1) {
                    rWins++;
                }

                matchResults.add(Math.max(0,blueHP)-Math.max(0,redHP));
            }

            // Calcuclate the average hit point difference and standard deviation
            CurrentDraws += draws;
            CurrentBWins += bWins;
            CurrentRWins += rWins;
            CurrentResults.addAll(matchResults);

            float sum = 0.0f, standardDeviation = 0.0f;

            for(int b = 0; b < CurrentResults.size(); b++){
                sum += CurrentResults.get(b);
            }

            float mean = sum/CurrentResults.size();

            for(int re : CurrentResults) {
                standardDeviation += Math.pow(re - mean, 2);
            }

            standardDeviation = (float)Math.sqrt(standardDeviation/CurrentResults.size());

            CurrentResults.clear();

            CurrentStdDev = standardDeviation;
            CurrentAverage = mean;
        } else {
            ArrayList<Thread> threadList = new ArrayList<Thread>();

            // Spin up multiple simulations across multiple threads
            for(int t = 0; t < threadsToUse; t++) {
                Thread thread = new Thread(() -> {
                    int runs = (int)Math.ceil(SimulationCount/threadsToUse);
                    int bWins = 0;
                    int rWins = 0;
                    int draws = 0;
                    ArrayList<Integer> matchResults = new ArrayList();

                    for(int i = 0; i < runs; i++){
                        try {
                            // Setup game conditions
                            PhysicalGameState pgs = PhysicalGameState.fromXML(MapXML, utt);
                            //PhysicalGameState pgs = PhysicalGameState.load(map, utt);

                            ArrayList<Unit> units = new ArrayList<>();

                            //Update unit health to match stats
                            for (Unit unit : pgs.getUnits()) {
                                if (unit.getType() == blight) {
                                    unit.setHitPoints(blight.hp);
                                    units.add(unit);
                                } else if (unit.getType() == rlight) {
                                    unit.setHitPoints(rlight.hp);
                                    units.add(unit);
                                }
                            }
                            GameState gs = new GameState(pgs, utt);

                            int MAXCYCLES = 350; // Maximum game length
                            int PERIOD = 25; // Refresh rate for display (milliseconds)

                            boolean gameover = false;

                            // Set the AIs
                            AI ai1 = new LightRush(utt, new NewStarPathFinding());
                            AI ai2 = new LightRush(utt, new NewStarPathFinding());

                            do {
                                PlayerAction pa1 = ai1.getAction(0, gs);
                                PlayerAction pa2 = ai2.getAction(1, gs);
                                gs.issueSafe(pa1);
                                gs.issueSafe(pa2);

                                // simulate:
                                gameover = gs.cycle();
                            } while (!gameover && gs.getTime() < MAXCYCLES);

                            // Track the units remaining hitpoints after a battle
                            int blueHP = 0;
                            int redHP = 0;

                            for(Unit u : units){
                                if (u.getType() == blight) {
                                    blueHP += u.getHitPoints();
                                } else if (u.getType() == rlight) {
                                    redHP += u.getHitPoints();
                                }
                            }

                            ai1.gameOver(gs.winner()); // TODO what do these do?
                            ai2.gameOver(gs.winner());

                            int result = gs.winner();
                            if (result == -1) {
                                draws++;
                            } else if (result == 0) {
                                bWins++;
                            } else if (result == 1) {
                                rWins++;
                            }

                            matchResults.add(Math.max(0,blueHP)-Math.max(0,redHP));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    CurrentDraws += draws;
                    CurrentBWins += bWins;
                    CurrentRWins += rWins;
                    CurrentResults.addAll(matchResults);
                });

                //Keep track of the thread and start it
                threadList.add(thread);
                thread.start();
            }

            while(threadList.size() > 0){
                for(int i = 0; i < threadList.size(); i++){
                    if(threadList.get(i).getState() == Thread.State.TERMINATED){
                        threadList.remove(threadList.get(i));
                    }
                }

                Thread.sleep(5);
            }

            // Calcuclate the average hit point difference and standard deviation
            float sum = 0.0f, standardDeviation = 0.0f;

            for(int b = 0; b < CurrentResults.size(); b++){
                sum += CurrentResults.get(b);
            }

            float mean = sum/CurrentResults.size();

            for(int re : CurrentResults) {
                standardDeviation += Math.pow(re - mean, 2);
            }

            standardDeviation = (float)Math.sqrt(standardDeviation/CurrentResults.size());

            CurrentResults.clear();

            CurrentStdDev = standardDeviation;
            CurrentAverage = mean;
        }

        if(!display) {
            AverageHPDifference.setText("Avg HP Difference: " + CurrentAverage);
            StdDevHPDifference.setText("StdDev: " + CurrentStdDev);
            BlueWins.setText("Blue Wins: " + CurrentBWins);
            RedWins.setText("Red Wins: " + CurrentRWins);
            Draws.setText("Draws: " + CurrentDraws);
            WinRate = ((CurrentBWins + (CurrentDraws / 2.0f)) / (float) (CurrentBWins + CurrentRWins + CurrentDraws) * 100);
            BlueWinRate.setText("Blue Win-rate: " + WinRate + "%");
        }
    }

    // The explore funtion shows a simple GUI that allows selecting game variables to be analysed
    // After one or two variables have been selected a number of simulations are performed to visualize the variables impact on game balance
    public static void Explore(){
        JFrame frame = new JFrame("Explore Game Variables");
        JPanel p1 = new JPanel();
        p1.setLayout(new GridLayout(2,3));
        p1.setBorder(new EmptyBorder(20, 20, 20, 20));

        String[] gameVariables = new String[]{ "Health", "Damage", "Range", "Move Time", "Attack Time" };
        JList list = new JList(gameVariables);
        list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL);
        list.setVisibleRowCount(-1);
        list.setSelectedIndex(0);

        JScrollPane listScroller = new JScrollPane(list);
        p1.add(listScroller);

        AddItem = new JButton(">>");
        RemoveItem = new JButton("<<");
        RemoveItem.setEnabled(false);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(2,1));
        buttonPanel.setBorder(new EmptyBorder(50, 50, 50, 50));
        buttonPanel.add(AddItem);
        buttonPanel.add(RemoveItem);

        p1.add(buttonPanel);

        ListModel.clear();
        JList list2 = new JList(ListModel);
        list2.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        list2.setLayoutOrientation(JList.VERTICAL);
        list2.setVisibleRowCount(-1);

        JScrollPane listScroller2 = new JScrollPane(list2);
        p1.add(listScroller2);

        p1.add(new Panel());
        p1.add(new Panel());

        JButton analyseButton = new JButton("Analyse");
        analyseButton.addActionListener(e -> {
            if(Running == false){
                Running = true;
                Status.setText("Status: Analysing...");

                new Thread(() -> {
                    try {
                        if(ListModel.size() == 1){
                            frame.setVisible(false);

                            String gameVar = (String)ListModel.get(0);

                            BlueHP = HP;
                            BlueDamage = DMG;
                            BlueRange = RNG;
                            BlueMoveTime = MT;
                            BlueAttackTime = AT;
                            HPEntry.setText(String.valueOf(HP));
                            DMGEntry.setText(String.valueOf(DMG));
                            RNGEntry.setText(String.valueOf(RNG));
                            MTEntry.setText(String.valueOf(MT));
                            ATEntry.setText(String.valueOf(AT));

                            int gameVarMax = 0;

                            switch (gameVar){
                                case "Health":
                                    gameVarMax = HP*2;
                                    break;
                                case "Damage":
                                    gameVarMax = DMG*2;
                                    break;
                                case "Range":
                                    gameVarMax = RNG*2;
                                    break;
                                case "Move Time":
                                    gameVarMax = MT*2;
                                    break;
                                case "Attack Time":
                                    gameVarMax = AT*2;
                                    break;
                            }

                            String results = "";

                            for(int i = 0; i <= gameVarMax; i++){
                                Status.setText("Status: Analysing "+i+" / "+gameVarMax);

                                switch (gameVar){
                                    case "Health":
                                        BlueHP = i;
                                        HPEntry.setText(String.valueOf(i));
                                        break;
                                    case "Damage":
                                        BlueDamage = i;
                                        DMGEntry.setText(String.valueOf(i));
                                        break;
                                    case "Range":
                                        BlueRange = i;
                                        RNGEntry.setText(String.valueOf(i));
                                        break;
                                    case "Move Time":
                                        BlueMoveTime = i;
                                        MTEntry.setText(String.valueOf(i));
                                        break;
                                    case "Attack Time":
                                        BlueAttackTime = i;
                                        ATEntry.setText(String.valueOf(i));
                                        break;
                                }

                                RunSimulation(false, Runtime.getRuntime().availableProcessors());

                                if(i == 0){
                                    results += WinRate;
                                }else{
                                    results += ","+WinRate;
                                }
                            }

                            System.out.println("AnalyseSingle|"+gameVar+"|"+results+"|");
                        }else if(ListModel.size() == 2){
                            frame.setVisible(false);

                            String gameVar1 = (String)ListModel.get(0);
                            String gameVar2 = (String)ListModel.get(1);

                            BlueHP = HP;
                            BlueDamage = DMG;
                            BlueRange = RNG;
                            BlueMoveTime = MT;
                            BlueAttackTime = AT;
                            HPEntry.setText(String.valueOf(HP));
                            DMGEntry.setText(String.valueOf(DMG));
                            RNGEntry.setText(String.valueOf(RNG));
                            MTEntry.setText(String.valueOf(MT));
                            ATEntry.setText(String.valueOf(AT));

                            int gameVar1Max = 0;
                            int gameVar2Max = 0;

                            switch (gameVar1){
                                case "Health":
                                    gameVar1Max = HP*2;
                                    break;
                                case "Damage":
                                    gameVar1Max = DMG*2;
                                    break;
                                case "Range":
                                    gameVar1Max = RNG*2;
                                    break;
                                case "Move Time":
                                    gameVar1Max = MT*2;
                                    break;
                                case "Attack Time":
                                    gameVar1Max = AT*2;
                                    break;
                            }

                            switch (gameVar2){
                                case "Health":
                                    gameVar2Max = HP*2;
                                    break;
                                case "Damage":
                                    gameVar2Max = DMG*2;
                                    break;
                                case "Range":
                                    gameVar2Max = RNG*2;
                                    break;
                                case "Move Time":
                                    gameVar2Max = MT*2;
                                    break;
                                case "Attack Time":
                                    gameVar2Max = AT*2;
                                    break;
                            }

                            String results = "";

                            for(int i = gameVar1Max; i >= 0 ; i--){
                                switch (gameVar1) {
                                    case "Health":
                                        BlueHP = i;
                                        HPEntry.setText(String.valueOf(i));
                                        break;
                                    case "Damage":
                                        BlueDamage = i;
                                        DMGEntry.setText(String.valueOf(i));
                                        break;
                                    case "Range":
                                        BlueRange = i;
                                        RNGEntry.setText(String.valueOf(i));
                                        break;
                                    case "Move Time":
                                        BlueMoveTime = i;
                                        MTEntry.setText(String.valueOf(i));
                                        break;
                                    case "Attack Time":
                                        BlueAttackTime = i;
                                        ATEntry.setText(String.valueOf(i));
                                        break;
                                }

                                if(i != gameVar1Max)
                                    results += ":";

                                for(int o = 0; o <= gameVar2Max; o++){
                                    switch (gameVar2) {
                                        case "Health":
                                            BlueHP = o;
                                            HPEntry.setText(String.valueOf(o));
                                            break;
                                        case "Damage":
                                            BlueDamage = o;
                                            DMGEntry.setText(String.valueOf(o));
                                            break;
                                        case "Range":
                                            BlueRange = o;
                                            RNGEntry.setText(String.valueOf(o));
                                            break;
                                        case "Move Time":
                                            BlueMoveTime = o;
                                            MTEntry.setText(String.valueOf(o));
                                            break;
                                        case "Attack Time":
                                            BlueAttackTime = o;
                                            ATEntry.setText(String.valueOf(o));
                                            break;
                                    }

                                    Status.setText("Status: Analysing "+(((gameVar1Max-i)*(gameVar2Max+1))+o)+" / "+((gameVar1Max+1)*(gameVar2Max+1)));

                                    RunSimulation(false, Runtime.getRuntime().availableProcessors());

                                    if(o != 0)
                                        results += ",";

                                    results += WinRate;
                                }
                            }

                            System.out.println("AnalyseMulti|"+gameVar1+"|"+gameVar2+"|"+results+"|");
                        }
                    } catch (Exception e1){
                        System.out.println(e1);
                    }

                    Running = false;
                    Status.setText("Status: Idle");
                }).start();
            }
        });

        JPanel borderPanel = new JPanel();
        borderPanel.setBorder(new EmptyBorder(10, 50, 10, 50));
        borderPanel.add(analyseButton);

        p1.add(borderPanel);

        AddItem.addActionListener(e -> {
            String selected = (String)list.getSelectedValue();

            for(int i = 0; i < ListModel.getSize(); i++){
                if (ListModel.get(i) == selected) return;
            }

            RemoveItem.setEnabled(true);
            ListModel.addElement(selected);
            list2.setSelectedIndex(ListModel.size()-1);

            if(ListModel.size() == 2){
                AddItem.setEnabled(false);
            }
        });

        RemoveItem.addActionListener(e -> {
            String selected = (String)list2.getSelectedValue();

            AddItem.setEnabled(true);
            ListModel.removeElement(selected);
            list2.setSelectedIndex(ListModel.size()-1);

            if(ListModel.size() == 0){
                RemoveItem.setEnabled(false);
            }
        });

        frame.add(p1, BorderLayout.CENTER);
        frame.pack();
        frame.setMinimumSize(new Dimension(600, 300));
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        File file = new File("even_map.xml");
        SAXBuilder saxBuilder = new SAXBuilder();
        Document document = saxBuilder.build(file);
        MapXML = document.getRootElement();

        JFrame frame = new JFrame("RTS Game Disruptor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel p1 = new JPanel();
        p1.setLayout(new GridLayout(11,4));
        p1.setBorder(new EmptyBorder(20, 20, 20, 20));

        p1.add( new JLabel("Blue HP: "));

        HPEntry = new JTextField();
        HPEntry.setText(String.valueOf(BlueHP));
        HPEntry.getDocument().addDocumentListener((SL) e -> {
            BlueHP = Integer.parseInt(HPEntry.getText());
        });
        p1.add(HPEntry);

        p1.add( new JLabel("Generations: "));

        JTextField gen = new JTextField();
        gen.setText(String.valueOf(Generations));
        gen.getDocument().addDocumentListener((SL) e -> {
            Generations = Integer.parseInt(gen.getText());
        });
        p1.add(gen);

        p1.add( new JLabel("Blue Attack: "));

        DMGEntry = new JTextField();
        DMGEntry.setText(String.valueOf(BlueDamage));
        DMGEntry.getDocument().addDocumentListener((SL) e -> {
            BlueDamage = Integer.parseInt(DMGEntry.getText());
        });
        p1.add(DMGEntry);

        p1.add( new JLabel("Population: "));

        JTextField pop = new JTextField();
        pop.setText(String.valueOf(Population));
        pop.getDocument().addDocumentListener((SL) e -> {
            Population = Integer.parseInt(pop.getText());
        });
        p1.add(pop);

        p1.add( new JLabel("Blue Attack Range: "));

        RNGEntry = new JTextField();
        RNGEntry.setText(String.valueOf(BlueRange));
        RNGEntry.getDocument().addDocumentListener((SL) e -> {
            BlueRange = Integer.parseInt(RNGEntry.getText());
        });
        p1.add(RNGEntry);

        p1.add( new JLabel("Mutation Rate: "));

        JTextField mut = new JTextField();
        mut.setText(String.valueOf(MutationRate));
        mut.getDocument().addDocumentListener((SL) e -> {
            MutationRate = Float.parseFloat(mut.getText());
        });
        p1.add(mut);

        p1.add( new JLabel("Blue Move Time: "));

        MTEntry = new JTextField();
        MTEntry.setText(String.valueOf(BlueMoveTime));
        MTEntry.getDocument().addDocumentListener((SL) e -> {
            BlueMoveTime = Integer.parseInt(MTEntry.getText());
        });
        p1.add(MTEntry);

        p1.add( new JLabel("Crossover Rate: "));

        JTextField cross = new JTextField();
        cross.setText(String.valueOf(CrossoverRate));
        cross.getDocument().addDocumentListener((SL) e -> {
            CrossoverRate = Float.parseFloat(cross.getText());
        });
        p1.add(cross);

        p1.add( new JLabel("Blue Attack Time: "));

        ATEntry = new JTextField();
        ATEntry.setText(String.valueOf(BlueAttackTime));
        ATEntry.getDocument().addDocumentListener((SL) e -> {
            BlueAttackTime = Integer.parseInt(ATEntry.getText());
        });
        p1.add(ATEntry);

        p1.add( new JLabel("Map Selection: "));


        JComboBox mapList = new JComboBox(new String[]{ "even_map.xml", "red_map.xml" });
        mapList.setSelectedIndex(0);
        mapList.addActionListener( e -> {
            JComboBox cb = (JComboBox)e.getSource();
            String selected = (String)cb.getSelectedItem();

            try {
                File file1 = new File(selected);
                SAXBuilder saxBuilder1 = new SAXBuilder();
                Document document1 = saxBuilder1.build(file1);
                MapXML = document1.getRootElement();
            } catch (Exception e1){ }
        });

        p1.add(mapList);

        AverageHPDifference = new JLabel("Avg HP Difference: ");
        StdDevHPDifference = new JLabel("StdDev: ");

        p1.add(AverageHPDifference);
        p1.add(StdDevHPDifference);

        p1.add(new JLabel("Seed: "));
        SeedEntry = new JTextField();
        SeedEntry.setText(Seed);
        SeedEntry.getDocument().addDocumentListener((SL) e -> {
            Seed = SeedEntry.getText();
        });
        p1.add(SeedEntry);


        RedWins = new JLabel("Red Wins: ");
        BlueWins = new JLabel("Blue Wins: ");
        RedWins = new JLabel("Red Wins: ");
        Draws = new JLabel("Draws: ");
        BlueWinRate = new JLabel("Blue Win-rate: ");

        p1.add(BlueWins);
        p1.add(RedWins);
        p1.add(Draws);
        p1.add(BlueWinRate);

        p1.add( new JLabel("Simulation Count: "));

        JTextField simCount = new JTextField();
        simCount.setText(String.valueOf(SimulationCount));
        simCount.getDocument().addDocumentListener((SL) e -> {
            SimulationCount = Integer.parseInt(simCount.getText());
        });
        p1.add(simCount);

        JButton runButton = new JButton("Run Simulation");
        runButton.addActionListener(e -> {
            if(Running == false){
                Running = true;
                Status.setText("Status: Simulating...");

                new Thread(() -> {
                    try {
                        RunSimulation(false, Runtime.getRuntime().availableProcessors());
                    } catch (Exception e1){
                        System.out.println(e1);
                    }

                    Running = false;
                    Status.setText("Status: Idle");
                }).start();
            }
        });

        JPanel runPanel = new JPanel();
        runPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        runPanel.add(runButton);

        JButton showButton = new JButton("Show Simulation");
        showButton.addActionListener(e -> {
            if(Running == false){
                Running = true;
                new Thread(() -> {
                    try {
                        RunSimulation(true, 1);
                    } catch (Exception e1){
                        System.out.println(e1);
                    }

                    Running = false;
                }).start();
            }
        });

        JPanel showPanel = new JPanel();
        showPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        showPanel.add(showButton);

        p1.add(runPanel);
        p1.add(showPanel);

        p1.add( new JLabel("Target Win-rate: "));

        JTextField targetWinRate = new JTextField();
        targetWinRate.setText(String.valueOf(TargetWinRate));
        targetWinRate.getDocument().addDocumentListener((SL) e -> {
            TargetWinRate = Float.parseFloat(targetWinRate.getText());
        });
        p1.add(targetWinRate);

        JButton evolveButton = new JButton("Evolve");
        evolveButton.addActionListener(e -> {
            if (Running == false) {
                Running = true;
                Status.setText("Status: Evolving...");
                new Thread(() -> {
                    try {
                        if(Seed == null || Seed.isEmpty()){
                            Random random = new Random();
                            Seed = String.valueOf(random.nextInt(1000000));
                            SeedEntry.setText(Seed);
                        }

                        BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
                        System.out.println("Evolve|Simulation Count: " + SimulationCount + " Target Win-rate: " + TargetWinRate + " Target Weight:" + WinWeight + " Change Weight: " + ChangeWeight + " HP Weight: "+HpWeight+" Generations: "+Generations+" Population: "+Population+ " Mutation: "+MutationRate+" Crossover: "+CrossoverRate+" Seed: "+Seed);

                        int gens = 0;
                        float best = Float.MIN_VALUE;

                        HPEntry.setText(String.valueOf(HP));
                        DMGEntry.setText(String.valueOf(DMG));
                        RNGEntry.setText(String.valueOf(RNG));
                        MTEntry.setText(String.valueOf(MT));
                        ATEntry.setText(String.valueOf(AT));

                        while (true) {
                            String s = bufferRead.readLine();


                            if (s.contains("END")) {
                                System.out.println("Evolution Complete!");
                                break;
                            } else if (!s.isEmpty()) {
                                gens++;
                                Status.setText("Status: Evolving "+gens+" / "+Generations);

                                String[] individuals = s.split(" : ");
                                int[][] chromosome = new int[individuals.length][5];
                                for (int i = 0; i < individuals.length; i++) {
                                    String[] geneString = individuals[i].split(", ");
                                    for (int k = 0; k < 5; k++) {
                                        chromosome[i][k] = Integer.parseInt(geneString[k]);
                                    }
                                }

                                String fitness = "";

                                for (int i = 0; i < individuals.length; i++) {
                                    BlueHP = chromosome[i][0];
                                    BlueDamage = chromosome[i][1];
                                    BlueRange = chromosome[i][2];
                                    BlueMoveTime = chromosome[i][3];
                                    BlueAttackTime = chromosome[i][4];

                                    RunSimulation(false, Runtime.getRuntime().availableProcessors());

                                    float maximumHP = HP*4;
                                    float numberOfVariables = 5.0f;
                                    float changed = (numberOfVariables - (Math.abs(1.0f - ((float) chromosome[i][0] / HP)) + Math.abs(1.0f - ((float) chromosome[i][1] / DMG)) + Math.abs(1.0f - ((float) chromosome[i][2] / RNG)) + Math.abs(1.0f - ((float) chromosome[i][3] / MT)) + Math.abs(1.0f - ((float) chromosome[i][4] / AT)))) / numberOfVariables;
                                    float tWinRate = (100 - Math.abs(WinRate - TargetWinRate)) / 100f;
                                    float hpDif = 1.0f - (Math.abs(1.0f - (CurrentAverage / maximumHP))/2);
                                    float sum = ((tWinRate * WinWeight) + (changed * ChangeWeight) + (hpDif * HpWeight));

                                    // Update best game variables in UI
                                    if(sum > best){
                                        best = sum;
                                        HPEntry.setText(String.valueOf(BlueHP));
                                        DMGEntry.setText(String.valueOf(BlueDamage));
                                        RNGEntry.setText(String.valueOf(BlueRange));
                                        MTEntry.setText(String.valueOf(BlueMoveTime));
                                        ATEntry.setText(String.valueOf(BlueAttackTime));
                                    }

                                    fitness +=  sum + ":" + CurrentAverage + ":" + CurrentStdDev + ":" + WinRate + " ";
                                }

                                System.out.println(fitness.trim());
                                System.out.flush();
                            }
                        }

                        BlueHP = Integer.parseInt(HPEntry.getText());
                        BlueDamage = Integer.parseInt(DMGEntry.getText());
                        BlueRange = Integer.parseInt(RNGEntry.getText());
                        BlueMoveTime = Integer.parseInt(MTEntry.getText());
                        BlueAttackTime = Integer.parseInt(ATEntry.getText());

                        RunSimulation(false, Runtime.getRuntime().availableProcessors());

                    } catch (Exception e1) {
                        System.out.print(e1);
                        System.out.print(" ");
                        for (StackTraceElement el : e1.getStackTrace()) {
                            System.out.print(el.toString());
                            System.out.print(" ");
                        }
                        System.out.println();
                    }

                    Status.setText("Status: Idle");
                    Running = false;
                }).start();
            }
        });

        JPanel evolvePanel = new JPanel();
        evolvePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        evolvePanel.add(evolveButton);

        p1.add(evolvePanel);


        JButton exploreButton = new JButton("Explore");
        exploreButton.addActionListener(e -> {
            Explore();
        });

        JPanel explorePanel = new JPanel();
        explorePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        explorePanel.add(exploreButton);

        p1.add(explorePanel);

        p1.add( new JLabel("Win-rate Weight: "));

        JTextField winRateWeight = new JTextField();
        winRateWeight.setText(String.valueOf(WinWeight));
        winRateWeight.getDocument().addDocumentListener((SL) e -> {
            WinWeight = Float.parseFloat(winRateWeight.getText());
        });
        p1.add(winRateWeight);

        p1.add( new JLabel("Changed Weight: "));

        JTextField changedWeight = new JTextField();
        changedWeight.setText(String.valueOf(ChangeWeight));
        changedWeight.getDocument().addDocumentListener((SL) e -> {
            ChangeWeight = Float.parseFloat(changedWeight.getText());
        });
        p1.add(changedWeight);

        Status = new JLabel("Status: Idle");
        p1.add(Status);

        p1.add(new JLabel(""));

        p1.add( new JLabel("HP Weight: "));

        JTextField hpWeight = new JTextField();
        hpWeight.setText(String.valueOf(HpWeight));
        hpWeight.getDocument().addDocumentListener((SL) e -> {
            HpWeight = Float.parseFloat(hpWeight.getText());
        });
        p1.add(hpWeight);

        frame.add(p1, BorderLayout.CENTER);
        frame.pack();
        frame.setMinimumSize(new Dimension(600, 300));
        frame.setVisible(true);
    }
}
