print("Initializing...")

#Import dependancies
import random
import numpy as np
from subprocess import Popen, PIPE, CalledProcessError
from deap import creator, base, tools, algorithms
import matplotlib.pyplot as plt
import time, os

runonce = False

# New mutation function - randomally mutate the game variables checking the upper limit against the configured constraints
def mutateUniform(individual, indpb, limits):
    for i in range(len(individual)):
        if random.random() < indpb:
            rand = individual[i] + (random.gauss(0, 0.2) * (limits[i]))

            if( rand < 0):
                rand = abs(rand)

            individual[i] = max(0, min(rand, limits[i]))

    return individual,


# Open the MicroRTS Game Disruptor process and read responses
with Popen(["java", "-cp", "MicroRTS.jar", "tests.GameDisruptor"], stdin=PIPE, stdout=PIPE, stderr=PIPE, bufsize=1, universal_newlines=True) as p:
    for line in p.stdout:

        # If the user pressed the evolve button start running evolution process
        if('Evolve' in line):
            print("Evolving...")

            # The configuration settings for the evolution process
            metadata = line.split("|")[1]
            print(metadata)

            # The red teams unit stats
            HP = 20
            DMG = 10
            RNG = 2
            MT = 8
            AT = 6

            # The maximum values for the blue units game variables
            limits = [
                HP*2,
                DMG*2,
                RNG*2,
                MT*2,
                AT*2
            ]

            # Read the evolution parameters from the configuration
            gensize = int(metadata.split("Generations: ")[1].split(" ")[0])
            popsize = int(metadata.split("Population: ")[1].split(" ")[0])
            mutaterate = float(metadata.split("Mutation: ")[1].split(" ")[0])
            crossrate = float(metadata.split("Crossover: ")[1].split(" ")[0])
            seed = int(metadata.split("Seed: ")[1].split(" ")[0])

            # Configure the seed and calcuclate timestamp for the experiment
            random.seed(seed)
            timestamp = time.strftime("%Y%m%d-%H%M%S")

            # Create toolbox and configure fitness function if first run
            if(runonce == False):
                creator.create("FitnessMaximum", base.Fitness, weights=(1.0,))
                creator.create("Individual", list, fitness=creator.FitnessMaximum)
                runonce = True

            toolbox = base.Toolbox()

            # Register chromosome, mutate and cross-over
            toolbox.register("hp", random.randint, 0, HP*2)
            toolbox.register("dmg", random.randint, 0, DMG*2)
            toolbox.register("rng", random.randint, 0, RNG*2)
            toolbox.register("mt", random.randint, 0, MT*2)
            toolbox.register("at", random.randint, 0, AT*2)

            toolbox.register("individual", tools.initCycle, creator.Individual, (toolbox.hp, toolbox.dmg, toolbox.rng, toolbox.mt, toolbox.at), n=1)
            toolbox.register("population", tools.initRepeat, list, toolbox.individual)

            toolbox.register("mate", tools.cxOnePoint)
            toolbox.register("mutate", mutateUniform, indpb=mutaterate, limits=limits)
            toolbox.register("select", tools.selTournament, tournsize=2)

            # Initial population
            population = toolbox.population(n=popsize)

            # Setup hall of fame
            elite = 1
            hof = tools.HallOfFame(100)

            # Setup the folders for the experiment
            if not os.path.exists('experiments'):
                os.makedirs('experiments')

            if not os.path.exists('experiments/'+timestamp):
                os.makedirs('experiments/'+timestamp)

            # Track the best individuals and the highest and average fitness
            goatgen = 0
            goatscore = 0
            goatchromosome = []

            avgs = []
            highs = []

            # Run the evolution process for the configured number of generations
            for gen in range(gensize):
                print("Running gen: "+str(gen))

                # Create new offspring
                offspring = algorithms.varAnd(population, toolbox, cxpb=crossrate, mutpb=1.0)

                # Add elite individuals to the offspring
                elitehof = min(elite, len(hof))
                for i in range(elitehof):
                    offspring.append(hof[i])

                # Ensure the game variables are all within their limits
                for ind in offspring:
                    for i in range(len(ind)):
                        ind[i] = max(0, min(limits[i], int(ind[i])))

                outputOffspring = ""
                for off in offspring:
                    for ind in off:
                        outputOffspring += str(int(ind))+", "
                    outputOffspring += ' : '

                # Send offspring to MicroRTS java process for evaluation
                p.stdin.write(outputOffspring+"\r\n")
                p.stdin.flush()

                # Read response back from MicroRTS
                response = p.stdout.readline()

                if not response:
                    break

                fits = response.split(" ")

                # Get fitness scores
                for s, ind in zip(fits, offspring):
                    split = s.split(":")
                    ind.fitness.values = (float(split[0]),)
                    setattr(ind, 'gen', gen)
                    setattr(ind, 'avg', float(split[1]))
                    setattr(ind, 'stdev', float(split[2]))
                    setattr(ind, 'winrate', float(split[3]))
                p.stdout.flush()

                # Update Hall of Fame
                hof.update(offspring)

                # select the population factoring in the elites
                elitehof = min(elite, len(hof))
                if elitehof > 0:
                    population = toolbox.select(offspring, k=popsize - elitehof)
                else:
                    population = toolbox.select(offspring, k=popsize)

                # Calculate stats for this gen
                high = 0
                avg = 0
                for n, a in enumerate(fits):
                    fit = float(a.split(":")[0])
                    avg += fit
                    high = max(high, fit)
                    if fit > goatscore:
                        goatgen = gen
                        goatscore = fit
                        goatchromosome = offspring[n][:]

                avgs.append(avg / len(offspring))
                highs.append(high)

                # record hall of fame
                outputString = "Hall of fame:\n"
                for h in range(len(hof)):
                    dif = hof[h][:]
                    dif[0] -= HP
                    dif[1] -= DMG
                    dif[2] -= RNG
                    dif[3] -= MT
                    dif[4] -= AT
                    outputString += "Gen: " + str(hof[h].gen) + " \tFit: " + str(
                        round(hof[h].fitness.values[0], 2)) + " \t" + str(hof[h]) + " \t" + " \tWin-Rate: " + "{:.2f}".format(hof[h].winrate) + " \tAvg HP Diff: " + "{:.2f}".format(hof[h].avg) + " \tStdev: " + "{:.2f}".format(hof[h].stdev) + " \t" + str(dif) + "\n"

                # Store the hall of fame in a file in the experiments folder
                with open("experiments/"+timestamp+"/halloffame.txt", "w") as outfile:
                    outfile.write(outputString)

            p.stdin.write("END\r\n")

            # output final hall of fame
            outputString = "Hall of fame:\n"
            outputString += "Generations: " + str(gensize) + " Population: " + str(popsize) + " Mutation-rate: " + str(mutaterate) + " Crossover-rate: " + str(crossrate) + " " + metadata + "\n"
            for h in range(len(hof)):
                dif = hof[h][:]
                dif[0] -= HP
                dif[1] -= DMG
                dif[2] -= RNG
                dif[3] -= MT
                dif[4] -= AT
                outputString += "Gen: " + str(hof[h].gen) + " \tFit: " + str(round(hof[h].fitness.values[0], 2)) + " \t" + str(hof[h])+ " \tWin-Rate: " + "{:.2f}".format(hof[h].winrate) + " \tAvg HP Diff: " + "{:.2f}".format(hof[h].avg) + " \tStdev: " + "{:.2f}".format(hof[h].stdev) + " \t" + str(dif) + "\n"

            # Store the final hall of fame in a file in the experiments folder
            with open("experiments/"+timestamp+"/halloffame.txt", "w") as outfile:
                outfile.write(outputString)

            print(outputString)

            # Store the graph of max & average fitness
            plt.style.use('seaborn')
            gens = range(gensize)
            fig, ax = plt.subplots()
            ax.plot(gens, avgs, label='Average')
            ax.plot(gens, highs, label='Highest')
            ax.set(xlabel='Generation', ylabel='Fitness', title='Fitness Per Generation')
            plt.tight_layout()
            plt.legend()
            fig.savefig("experiments/"+timestamp+"/fitness.png")

        elif('AnalyseSingle' in line):
            # Read in game variable balance results to show graph
            gameVar = line.split("|")[1]
            results = line.split("|")[2].split(",")

            # Convert result string to float array
            for i in range(len(results)):
                results[i] = float(results[i])

            # Show balance graph for a game
            plt.style.use('seaborn')

            vals = range(len(results))
            yticks = [0,10,20,30,40,50,60,70,80,90,100]

            fig, ax = plt.subplots()
            plt.xticks(vals)
            plt.yticks(yticks)
            plt.axhline(color='red', lw=0.2, y=50)
            plt.axvline(color='red', lw=0.2, x=(len(results)-1)/2)

            ax.plot(vals, results)
            ax.set(xlabel=gameVar, ylabel='Win-Rate', title=gameVar+' Balance')
            ax.set_ylim([-2,102])
            ax.set_yticklabels([str(x)+'%' for x in yticks])

            plt.tight_layout()
            plt.show()

            plt.close(fig)

        elif ('AnalyseMulti' in line):
            # Read in multi game variable balance results to show heatmap
            gameVar1 = line.split("|")[1]
            gameVar2 = line.split("|")[2]
            results = line.split("|")[3].split(":")

            multi = []

            # Convert result string to multi-dimension array
            for r in results:
                arr = r.split(",")

                for i in range(len(arr)):
                    arr[i] = float(arr[i])

                multi.append(arr)

            # Prepare results for heat map
            data = np.array(multi)

            gameVar1Max = len(multi)
            gameVar2Max = len(multi[0])

            vals1 = range(gameVar1Max)
            vals2 = range(gameVar2Max)

            # Show heatmap of game balance of two game variables
            plt.style.use('default')

            fig, ax = plt.subplots()
            im = ax.imshow(data, cmap=plt.cm.bwr)

            ax.set_xticks(np.arange(gameVar2Max))
            ax.set_yticks(np.arange(gameVar1Max))

            ax.set_xticklabels(vals2)
            ax.set_yticklabels(reversed(vals1))

            cbar = ax.figure.colorbar(im, ax=ax)
            cbar.ax.set_ylabel("Win-Rate", rotation=-90, va="bottom")
            plt.setp(ax.get_xticklabels(), rotation=45, ha="right", rotation_mode="anchor")

            ax.set_title(gameVar1+" vs "+gameVar2)
            plt.xlabel(gameVar2)
            plt.ylabel(gameVar1)

            fig.tight_layout()
            plt.show()

            plt.close(fig)
        else:
            print(line, end='')  # process line here

    for line in p.stderr:
        print(line, end='')

# Raise error of MicroRTS process errored
if p.returncode != 0:
    raise CalledProcessError(p.returncode, p.args)