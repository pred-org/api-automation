# Copy this file to set_env.sh, fill in your real values, then run:
#   source scripts/set_env.sh
# before: mvn test
# Do not commit set_env.sh (it is in .gitignore).

export PRIVATE_KEY=          # Your EOA private key (hex, no 0x prefix)
export EOA_ADDRESS=          # Your EOA address (0x...)
export API_BASE_URI_PUBLIC=https://uat-frankfurt.pred.app
export API_BASE_URI_INTERNAL=http://api-internal.uat-frankfurt.pred.app
export SIG_SERVER_URL=http://localhost:5050
export MARKET_ID=            # e.g. 0x0b5e4867f18d734efa40ff8c9144393e0fda072f90d865a2a8b086c02ccbb900
export DEPOSIT_AMOUNT=1000000000
