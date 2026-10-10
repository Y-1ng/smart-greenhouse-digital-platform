const fs = require('node:fs')
const path = require('node:path')
const solc = require('solc')
const { ethers } = require('ethers')

const CONTRACT_FILE = path.join(
  __dirname,
  '智慧温室存证合约_SmartGreenhouseRegistry.sol',
)

const ROLE = {
  NONE: 0,
  ADMIN: 1,
  DEVICE_SERVICE: 2,
  QUALITY_ADMIN: 3,
  BUSINESS_SERVICE: 4,
  REGULATOR: 5,
}

function compileContract(options = {}) {
  const evmVersion = options.evmVersion ?? 'istanbul'
  const source = fs.readFileSync(CONTRACT_FILE, 'utf8')
  const input = {
    language: 'Solidity',
    sources: { 'SmartGreenhouseRegistry.sol': { content: source } },
    settings: {
      optimizer: { enabled: true, runs: 200 },
      evmVersion,
      outputSelection: {
        '*': { '*': ['abi', 'evm.bytecode.object'] },
      },
    },
  }
  const output = JSON.parse(solc.compile(JSON.stringify(input)))
  const errors = (output.errors || []).filter(
    (item) => item.severity === 'error',
  )
  if (errors.length > 0) {
    throw new Error(errors.map((item) => item.formattedMessage).join('\n'))
  }
  const contract =
    output.contracts['SmartGreenhouseRegistry.sol'].SmartGreenhouseRegistry
  return {
    abi: contract.abi,
    bytecode: `0x${contract.evm.bytecode.object}`,
    bin: contract.evm.bytecode.object,
    solcVersion: solc.version(),
    evmVersion,
  }
}

function createLocalChain() {
  const ganache = require('ganache')
  const eip1193 = ganache.provider({
    logging: { quiet: true },
    wallet: { deterministic: true, totalAccounts: 10 },
  })
  return { eip1193, provider: new ethers.BrowserProvider(eip1193) }
}

function createRpcChain(rpcUrl, privateKey) {
  const provider = new ethers.JsonRpcProvider(rpcUrl)
  const signer = privateKey
    ? new ethers.Wallet(privateKey, provider)
    : null
  return { provider, signer }
}

async function deployContract(signer, compiled) {
  const factory = new ethers.ContractFactory(
    compiled.abi,
    compiled.bytecode,
    signer,
  )
  const contract = await factory.deploy()
  const receipt = await contract.deploymentTransaction().wait()
  return {
    address: await contract.getAddress(),
    transactionHash: receipt.hash,
    blockNumber: receipt.blockNumber,
    contract,
  }
}

async function grantRole(contract, adminSigner, account, role) {
  const tx = await contract.connect(adminSigner).setRole(account, role, true)
  const receipt = await tx.wait()
  return {
    account,
    role,
    transactionHash: receipt.hash,
    blockNumber: receipt.blockNumber,
  }
}

function toBytes32(value) {
  const text = value === undefined || value === null ? '' : String(value)
  if (/^0x[0-9a-fA-F]{64}$/.test(text)) {
    return text
  }
  if (/^[0-9a-fA-F]{64}$/.test(text)) {
    return `0x${text}`
  }
  return ethers.sha256(ethers.toUtf8Bytes(text))
}

module.exports = {
  ROLE,
  CONTRACT_FILE,
  compileContract,
  createLocalChain,
  createRpcChain,
  deployContract,
  grantRole,
  toBytes32,
}
