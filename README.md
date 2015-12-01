# Kt2048
AI for the [2048](http://gabrielecirulli.github.io/2048/) game, implemented in [Kotlin](https://kotlinlang.org/)!

This AI uses a Expectimax search routine, coupled with a heuristic scoring function courtesy of @nneonneo.
The entire board (4x4 tiles) is encoded as two 64-bit integers. Moves are pre-calculated and stored in a lookup table for fast access.
The search is parallelized by running subsearches from each top-level move on different threads, and ultimately combining the results.
A transposition table is shared across threads and used to save and lookup previously scored grids.

## Running the AI
To run the game, the program utilizes [Selenium](http://www.seleniumhq.org/) to interface with a browser of your choice.

### Firefox (Requires working Firefox installation)
Navigate to project directory and run

      ./gradlew run -Dexec.args="firefox"

### Chrome (Requires working Chrome installation and ChromeDriver executable)
Download the ChromeDriver executable from https://sites.google.com/a/chromium.org/chromedriver/downloads
Navigate to project directory and run

      ./gradlew run -Dexec.args="chrome [path-to-chromedriver]"
      e.g. ./gradlew run -Dexec.args="chrome C:/chromedriver.exe"
      
      
### Enjoy!
![highscore 2](https://cloud.githubusercontent.com/assets/11733067/11515247/f3b8b84e-984a-11e5-8aff-8efbc2b41d46.png)
