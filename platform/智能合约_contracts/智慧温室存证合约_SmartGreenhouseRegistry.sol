// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/// @title 智慧温室全链条存证合约
/// @notice 保存环境根哈希、合格证摘要、一物一码、扫码记录和追溯节点。
contract SmartGreenhouseRegistry {
    enum Role {
        NONE,
        ADMIN,
        DEVICE_SERVICE,
        QUALITY_ADMIN,
        BUSINESS_SERVICE,
        REGULATOR
    }

    struct EnvironmentProof {
        bytes32 rootHash;
        string deviceId;
        uint256 timestamp;
        bool exists;
    }

    struct TraceCodeProof {
        bytes32 signatureHash;
        string batchId;
        string status;
        uint256 timestamp;
        bool exists;
    }

    struct ScanRecord {
        address scanner;
        string channel;
        string result;
        uint256 timestamp;
    }

    struct CertificateProof {
        bytes32 contentHash;
        bytes32 signatureHash;
        string status;
        uint256 timestamp;
        bool exists;
    }

    struct TraceNode {
        bytes32 nodeHash;
        bytes32 previousHash;
        string stageType;
        address operatorAddress;
        uint256 timestamp;
    }

    mapping(address => mapping(Role => bool)) private roleFlags;
    mapping(address => Role) private primaryRoles;
    mapping(string => EnvironmentProof) private environmentProofs;
    mapping(string => TraceCodeProof) private traceCodes;
    mapping(string => ScanRecord[]) private scanRecords;
    mapping(string => CertificateProof) private certificates;
    mapping(string => TraceNode[]) private traceNodes;
    mapping(string => string) private fieldProductCodes;

    event RoleUpdated(address indexed account, Role indexed role, bool enabled);
    event EnvironmentRootRecorded(string cycleId, bytes32 rootHash, string deviceId);
    event TraceCodeRegistered(string productCode, bytes32 signatureHash, string batchId);
    event ScanRecorded(string productCode, address indexed scanner, string result);
    event CertificateRecorded(string certificateId, bytes32 contentHash, string status);
    event CertificateSignatureRecorded(string certificateId, bytes32 contentHash, bytes32 signatureHash);
    event TraceNodeAppended(string fieldNo, bytes32 nodeHash, bytes32 previousHash, string stageType);
    event FieldCodeBound(string fieldNo, string productCode);

    constructor() {
        roleFlags[msg.sender][Role.ADMIN] = true;
        primaryRoles[msg.sender] = Role.ADMIN;
        emit RoleUpdated(msg.sender, Role.ADMIN, true);
    }

    modifier onlyRole(Role expectedRole) {
        require(
            roleFlags[msg.sender][Role.ADMIN] || roleFlags[msg.sender][expectedRole],
            "role denied"
        );
        _;
    }

    function setRole(address account, Role role, bool enabled) external onlyRole(Role.ADMIN) {
        roleFlags[account][role] = enabled;
        if (enabled) {
            primaryRoles[account] = role;
        } else if (primaryRoles[account] == role) {
            primaryRoles[account] = roleFlags[account][Role.ADMIN] ? Role.ADMIN : Role.NONE;
        }
        emit RoleUpdated(account, role, enabled);
    }

    function roleOf(address account) external view returns (Role) {
        return primaryRoles[account];
    }

    function hasRole(address account, Role role) external view returns (bool) {
        return roleFlags[account][role];
    }

    function recordEnvironmentRoot(
        string calldata cycleId,
        bytes32 rootHash,
        string calldata deviceId
    ) external onlyRole(Role.DEVICE_SERVICE) {
        require(rootHash != bytes32(0), "root hash required");

        // 幂等存证：同一周期内容一致时允许重复上链，内容不一致则拒绝，防止链上数据被覆盖。
        EnvironmentProof storage proof = environmentProofs[cycleId];
        if (proof.exists) {
            require(
                proof.rootHash == rootHash,
                "cycle already recorded with different root"
            );
        } else {
            proof.rootHash = rootHash;
            proof.deviceId = deviceId;
            proof.timestamp = block.timestamp;
            proof.exists = true;
        }
        emit EnvironmentRootRecorded(cycleId, rootHash, deviceId);
    }

    function getEnvironmentProof(
        string calldata cycleId
    ) external view returns (bool exists, bytes32 rootHash, string memory deviceId, uint256 timestamp) {
        EnvironmentProof storage proof = environmentProofs[cycleId];
        return (proof.exists, proof.rootHash, proof.deviceId, proof.timestamp);
    }

    function registerTraceCode(
        string calldata productCode,
        bytes32 signatureHash,
        string calldata batchId
    ) external onlyRole(Role.QUALITY_ADMIN) {
        require(signatureHash != bytes32(0), "signature hash required");

        // 幂等存证：同一溯源码签名一致时允许重复上链，签名不一致则拒绝。
        TraceCodeProof storage proof = traceCodes[productCode];
        if (proof.exists) {
            require(
                proof.signatureHash == signatureHash,
                "trace code already registered with different signature"
            );
        } else {
            proof.signatureHash = signatureHash;
            proof.batchId = batchId;
            proof.status = "ACTIVE";
            proof.timestamp = block.timestamp;
            proof.exists = true;
        }
        emit TraceCodeRegistered(productCode, signatureHash, batchId);
    }

    function verifyTraceCode(
        string calldata productCode
    ) external view returns (
        bool exists,
        bytes32 signatureHash,
        string memory batchId,
        string memory status,
        uint256 scanCount
    ) {
        TraceCodeProof storage proof = traceCodes[productCode];
        return (
            proof.exists,
            proof.signatureHash,
            proof.batchId,
            proof.status,
            scanRecords[productCode].length
        );
    }

    function appendScanRecord(
        string calldata productCode,
        string calldata channel,
        string calldata result
    ) external {
        require(traceCodes[productCode].exists, "trace code not found");
        require(bytes(channel).length > 0, "channel required");

        scanRecords[productCode].push(ScanRecord({
            scanner: msg.sender,
            channel: channel,
            result: result,
            timestamp: block.timestamp
        }));
        emit ScanRecorded(productCode, msg.sender, result);
    }

    function getScanRecord(
        string calldata productCode,
        uint256 index
    ) external view returns (address scanner, string memory channel, string memory result, uint256 timestamp) {
        require(index < scanRecords[productCode].length, "scan index out of range");
        ScanRecord storage record = scanRecords[productCode][index];
        return (record.scanner, record.channel, record.result, record.timestamp);
    }

    function recordCertificate(
        string calldata certificateId,
        bytes32 contentHash,
        string calldata status
    ) external onlyRole(Role.QUALITY_ADMIN) {
        require(contentHash != bytes32(0), "content hash required");

        // 幂等存证：同一合格证摘要一致时允许重复上链，摘要不一致则拒绝。
        CertificateProof storage proof = certificates[certificateId];
        if (proof.exists) {
            require(
                proof.contentHash == contentHash,
                "certificate already recorded with different content"
            );
        } else {
            proof.contentHash = contentHash;
            proof.signatureHash = bytes32(0);
            proof.status = status;
            proof.timestamp = block.timestamp;
            proof.exists = true;
        }
        emit CertificateRecorded(certificateId, contentHash, status);
    }

    function recordCertificateSignature(
        string calldata certificateId,
        bytes32 contentHash,
        bytes32 signatureHash
    ) external onlyRole(Role.QUALITY_ADMIN) {
        CertificateProof storage proof = certificates[certificateId];
        require(proof.exists, "certificate not found");
        require(proof.contentHash == contentHash, "content hash mismatch");
        require(signatureHash != bytes32(0), "signature hash required");

        proof.signatureHash = signatureHash;
        emit CertificateSignatureRecorded(certificateId, contentHash, signatureHash);
    }

    function appendTraceNode(
        string calldata fieldNo,
        uint256 index,
        bytes32 nodeHash,
        bytes32 previousHash,
        string calldata stageType
    ) external onlyRole(Role.BUSINESS_SERVICE) {
        require(nodeHash != bytes32(0), "node hash required");

        TraceNode[] storage nodes = traceNodes[fieldNo];
        if (index < nodes.length) {
            // 幂等重放：同一位置必须内容一致，拒绝覆盖已存证的追溯历史。
            require(nodes[index].nodeHash == nodeHash, "trace node not match");
            emit TraceNodeAppended(fieldNo, nodeHash, previousHash, stageType);
            return;
        }
        require(index == nodes.length, "trace node out of order");

        bytes32 expectedHash = nodes.length == 0 ? bytes32(0) : nodes[nodes.length - 1].nodeHash;
        require(previousHash == expectedHash, "previous hash mismatch");

        nodes.push(TraceNode({
            nodeHash: nodeHash,
            previousHash: previousHash,
            stageType: stageType,
            operatorAddress: msg.sender,
            timestamp: block.timestamp
        }));
        emit TraceNodeAppended(fieldNo, nodeHash, previousHash, stageType);
    }

    function getTraceNode(
        string calldata fieldNo,
        uint256 index
    ) external view returns (
        bytes32 nodeHash,
        bytes32 previousHash,
        string memory stageType,
        address operatorAddress,
        uint256 timestamp
    ) {
        require(index < traceNodes[fieldNo].length, "trace node index out of range");
        TraceNode storage node = traceNodes[fieldNo][index];
        return (
            node.nodeHash,
            node.previousHash,
            node.stageType,
            node.operatorAddress,
            node.timestamp
        );
    }

    function traceNodeCount(string calldata fieldNo) external view returns (uint256) {
        return traceNodes[fieldNo].length;
    }

    function bindFieldCode(
        string calldata fieldNo,
        string calldata productCode
    ) external onlyRole(Role.QUALITY_ADMIN) {
        require(traceCodes[productCode].exists, "trace code not found");
        require(bytes(fieldProductCodes[fieldNo]).length == 0, "field already bound");

        fieldProductCodes[fieldNo] = productCode;
        emit FieldCodeBound(fieldNo, productCode);
    }

    function productCodeOfField(string calldata fieldNo) external view returns (string memory) {
        return fieldProductCodes[fieldNo];
    }
}
