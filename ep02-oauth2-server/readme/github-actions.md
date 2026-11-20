# JUnit checks on GitHub

## Install the workflow at the repository root

GitHub only discovers workflows in the repository root's `.github/workflows`.
This workspace is restricted to `ep02-oauth2-server`, so the prepared workflow
must be moved one directory up before GitHub can run it.

From `ep02-oauth2-server`, run:

```bash
mkdir -p ../.github/workflows
mv .github/workflows/junit.yml ../.github/workflows/junit.yml
```

Commit the root workflow together with your project changes and push it. Its
commands run inside `ep02-oauth2-server` and test the parent Maven reactor:
auth-server, consumer-service, and management-service. Modules without tests
are allowed. Java 25 matches the parent POM. Current JUnit tests use mocks and an embedded H2 JDBC database;
they do not require PostgreSQL, Mailpit, or Kafka services.

The workflow runs on main pushes, pull requests targeting main, merge queue
checks, and manual runs. It uploads available Surefire reports even if tests
fail. It has no write permission and never pushes or merges code.

## Prevent failing changes from entering main

An Actions workflow executes after GitHub receives a push. It cannot cancel
or undo that push. Protect main and require the tests before merging instead:

1. Open repository Settings → Branches → Add branch protection rule.
2. Set the branch pattern to `main`.
3. Enable **Require a pull request before merging**.
4. Enable **Require status checks to pass before merging** and select
   **JUnit tests (ep02-oauth2-server)** after its first workflow run.
5. Enable **Require branches to be up to date before merging**.
6. Enable **Do not allow bypassing the above settings** if administrators
   must follow the same rules. Leave force pushes disabled.

Repository plans and permissions determine which protection options are
available. Repository settings are not modified by this workflow.

After setup, push changes to a feature branch and open a pull request to main.
A failing test blocks merging; a passing check makes the PR eligible to merge,
subject to the other repository rules. Direct main pushes are restricted by
the protection rule, not by the workflow.
