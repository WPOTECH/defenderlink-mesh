import React, { useState } from 'react';
import { T } from './theme';
import { Card, Badge, Indicator, Btn, InfoRow } from './components';
import type { ActiveTunnel } from './types';
import { ago } from './utils';

interface Props {
  id: string;
  tunnel: ActiveTunnel;
  onDisconnect: (id: string) => void;
}

export function TunnelCard({ id, tunnel, onDisconnect }: Props) {
  const [confirmDisc, setConfirmDisc] = useState(false);
  const stColor = tunnel.state === 'ACTIVE' ? T.accent : tunnel.state === 'FAILED' ? T.danger : T.warn;

  return (
    <Card style={{ borderLeft: `2px solid ${stColor}40` }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
        <div>
          <div style={{ fontSize: 13, fontWeight: 600, color: T.textHi, fontFamily: T.display, marginBottom: 2 }}>
            {tunnel.serviceId}
          </div>
          <div style={{ fontSize: 10, color: T.textDim, fontFamily: T.font }}>
            iface: {tunnel.interfaceName}
          </div>
        </div>
        <Badge color={stColor}>
          <Indicator color={stColor} pulse={tunnel.state === 'ACTIVE'} />
          {tunnel.state}
        </Badge>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 10 }}>
        <InfoRow label="Local IP" value={tunnel.localIp} />
        <InfoRow label="Remote IP" value={tunnel.remoteIp} />
        <InfoRow label="Endpoint" value={tunnel.remoteEndpoint} />
        <InfoRow label="Protocol" value={tunnel.protocol?.toUpperCase()} />
        <InfoRow label="Listen port" value={tunnel.listenPort} />
        <InfoRow label="Created" value={ago(tunnel.createdAt)} />
      </div>

      {tunnel.error && (
        <div style={{
          fontSize: 10, color: T.danger, padding: '6px 8px', borderRadius: 3,
          background: T.dangerBg, marginBottom: 8, wordBreak: 'break-word',
        }}>{tunnel.error}</div>
      )}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 6 }}>
        {confirmDisc ? (
          <>
            <Btn small variant="ghost" onClick={() => setConfirmDisc(false)}>Cancel</Btn>
            <Btn small variant="danger" onClick={() => { onDisconnect(id); setConfirmDisc(false); }}>
              Confirm
            </Btn>
          </>
        ) : (
          <Btn small variant="danger" onClick={() => setConfirmDisc(true)}>Disconnect</Btn>
        )}
      </div>
    </Card>
  );
}
