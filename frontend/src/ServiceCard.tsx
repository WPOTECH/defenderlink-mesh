import React, { useState } from 'react';
import { T } from './theme';
import { Card, Badge, Btn, InfoRow } from './components';
import type { ServiceRecord } from './types';
import { short, ago } from './utils';

interface Props {
  svc: ServiceRecord;
  isMine: boolean;
  onConnect: (id: string) => void;
  onRevoke: (id: string) => void;
}

export function ServiceCard({ svc, isMine, onConnect, onRevoke }: Props) {
  const [confirmRevoke, setConfirmRevoke] = useState(false);

  return (
    <Card style={{ borderLeft: `2px solid ${svc.active ? T.info : T.textDim}40` }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: T.textHi, fontFamily: T.display, marginBottom: 2 }}>
            {svc.serviceId}
          </div>
          <div style={{ fontSize: 10, color: T.textDim, fontFamily: T.font }}>
            {svc.protocol?.toUpperCase()} &middot; owner: {short(svc.ownerNodeId)}
          </div>
        </div>
        <Badge color={svc.active ? T.info : T.textDim}>
          {svc.active ? 'ACTIVE' : 'REVOKED'}
        </Badge>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 10 }}>
        <InfoRow label="Local bind" value={svc.localBind} />
        <InfoRow label="Proxy port" value={svc.assignedPort} />
        <InfoRow label="Allowed peers" value={svc.allowedNodes?.length || 0} />
        <InfoRow label="Exposed" value={ago(svc.exposedAt)} />
      </div>

      {svc.allowedNodes?.length > 0 && (
        <div style={{ fontSize: 10, color: T.textDim, marginBottom: 8 }}>
          Peers: {svc.allowedNodes.map(n => short(n)).join(', ')}
        </div>
      )}

      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
        {!isMine && svc.active && (
          <Btn small variant="primary" onClick={() => onConnect(svc.serviceId)}>
            {'\u25B6'} Connect
          </Btn>
        )}
        {isMine && svc.active && (
          confirmRevoke ? (
            <>
              <Btn small variant="ghost" onClick={() => setConfirmRevoke(false)}>Cancel</Btn>
              <Btn small variant="danger" onClick={() => { onRevoke(svc.serviceId); setConfirmRevoke(false); }}>
                Confirm Revoke
              </Btn>
            </>
          ) : (
            <Btn small variant="danger" onClick={() => setConfirmRevoke(true)}>Revoke</Btn>
          )
        )}
      </div>
    </Card>
  );
}
