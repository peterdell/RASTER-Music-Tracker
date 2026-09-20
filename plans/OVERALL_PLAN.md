Read README.md in the root folder to understand the project's purpose and history.
Also read the documents linked in the README.md that are part of the this repository.

Here is my plan for this project. This will span many sessions and take a long time.
Take notes, so we can continue later and start parallel sessions where required.
Be open asking for design decisions and present the options with advantages and disadvantages.

Ultimately, I will port it from C++ to Java, as I did with "C:\jac\system\Windows\Programming\Repositories\dis6502" to "C:\jac\system\Java\Programming\Repositories\dis6502".
First, create a separate folder named  "cpp" in the "src" folder.
Move all files to the "cpp" subfolder and adapt references as required.
Ensure the existing build always works.
Then, we have to clean up the existing CPP source and add tests first, because without tests for the current behavior, we cannot validate the correctness of the port later.
Be aware that the current code fully mixes UI and model and uses timers to run sound generation in parallel.

