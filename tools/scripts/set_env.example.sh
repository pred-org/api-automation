# Optional / legacy: project uses .env at repo root as single config source.
# Java loads .env via Config; sig-server loads it on startup. You do not need this file.
# If you prefer exporting in shell instead of .env, copy to set_env.sh, fill values, then:
#   source tools/scripts/set_env.sh
# before mvn test or npm start. Do not commit set_env.sh (in .gitignore).

export PRIVATE_KEY=          # Your EOA private key (hex, no 0x prefix)
export EOA_ADDRESS=          # Your EOA address (0x...)
export API_BASE_URI_PUBLIC=https://uat-frankfurt.pred.app
export API_BASE_URI_INTERNAL=http://api-internal.uat-frankfurt.pred.app
export SIG_SERVER_URL=http://localhost:5050
export MARKET_ID=            # e.g. 0xf83d64fbb43a9b199109a96fee6291fc66b9fe0a5cd38b0bd2901fd10d7f1900
export DEPOSIT_AMOUNT=1000000000
