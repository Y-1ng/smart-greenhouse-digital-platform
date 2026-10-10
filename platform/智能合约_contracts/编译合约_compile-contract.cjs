const fs = require('node:fs')
const path = require('node:path')
const { compileContract } = require('./链上工具_chain-core.cjs')

const evmVersion = process.argv[2] ?? 'istanbul'
const compiled = compileContract({ evmVersion })

const abiPath = path.join(__dirname, '智慧温室存证合约_SmartGreenhouseRegistry.abi.json')
const binPath = path.join(__dirname, '智慧温室存证合约_SmartGreenhouseRegistry.bin')

fs.writeFileSync(abiPath, JSON.stringify(compiled.abi, null, 2), 'utf8')
fs.writeFileSync(binPath, compiled.bin, 'utf8')

const functions = compiled.abi
  .filter((item) => item.type === 'function')
  .map((item) => item.name)
  .sort()

console.log(
  JSON.stringify(
    {
      solc: compiled.solcVersion,
      evmVersion: compiled.evmVersion,
      functions: functions.length,
      bytecodeBytes: compiled.bytecode.length / 2 - 1,
      abiPath,
      binPath,
    },
    null,
    2,
  ),
)
