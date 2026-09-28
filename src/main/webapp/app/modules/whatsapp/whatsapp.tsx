import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Badge, Button, Card, Col, Form, Row, Spinner, Table } from 'react-bootstrap';
import { Translate, translate } from 'react-jhipster';

import { faCheckCircle, faPaperPlane, faQrcode, faSync } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import axios from 'axios';
import { toast } from 'react-toastify';

interface IWhatsappStatus {
  instanceName: string;
  state: string;
  connected: boolean;
  qrCode?: string | null;
  pairingCode?: string | null;
}

interface ISendResultItem {
  input: string;
  number?: string | null;
  success: boolean;
  error?: string | null;
}

interface ISendResult {
  total: number;
  sent: number;
  failed: number;
  results: ISendResultItem[];
}

const API_URL = 'api/whatsapp';
const QR_REFRESH_MS = 20000;
const STATUS_POLL_MS = 3000;
const MAX_TEXT_LENGTH = 4096;

const parseNumbers = (value: string): string[] =>
  value
    .split(/[\n,;]+/)
    .map(item => item.trim())
    .filter(item => item.length > 0);

const errorMessage = (error: any, fallbackKey: string): string =>
  error?.response?.data?.detail ?? error?.response?.data?.title ?? translate(fallbackKey);

export const WhatsappPage = () => {
  const [status, setStatus] = useState<IWhatsappStatus | null>(null);
  const [connecting, setConnecting] = useState(false);
  const [connectionError, setConnectionError] = useState<string | null>(null);
  const [qrSeconds, setQrSeconds] = useState(0);

  const [numbersText, setNumbersText] = useState('');
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [sendResult, setSendResult] = useState<ISendResult | null>(null);

  const requestInFlightRef = useRef(false);
  const connected = status?.connected ?? false;
  const numbers = useMemo(() => parseNumbers(numbersText), [numbersText]);

  const connect = useCallback(async (force = false, silent = false) => {
    if (requestInFlightRef.current) return;
    requestInFlightRef.current = true;
    if (!silent) {
      setConnecting(true);
      setConnectionError(null);
    }
    try {
      const { data } = await axios.post<IWhatsappStatus>(`${API_URL}/connect`, null, { params: { force }, timeout: 0 });
      setStatus(data);
      if (data.qrCode) {
        setQrSeconds(QR_REFRESH_MS / 1000);
      }
      if (!silent && !data.connected && !data.qrCode) {
        setConnectionError(translate('whatsapp.connection.qrUnavailable'));
      }
    } catch (error) {
      if (!silent) {
        setConnectionError(errorMessage(error, 'whatsapp.errors.connect'));
      }
    } finally {
      requestInFlightRef.current = false;
      if (!silent) setConnecting(false);
    }
  }, []);

  useEffect(() => {
    connect(false);
  }, [connect]);

  useEffect(() => {
    if (connected) return undefined;

    const refreshQr = setInterval(() => connect(false, true), QR_REFRESH_MS);
    const pollStatus = setInterval(async () => {
      try {
        const { data } = await axios.get<IWhatsappStatus>(`${API_URL}/status`);
        if (data.connected) {
          setStatus(data);
        }
      } catch {
        // A single failed poll is not worth surfacing; the next tick retries.
      }
    }, STATUS_POLL_MS);
    const countdown = setInterval(() => setQrSeconds(current => (current > 0 ? current - 1 : 0)), 1000);

    return () => {
      clearInterval(refreshQr);
      clearInterval(pollStatus);
      clearInterval(countdown);
    };
  }, [connected, connect]);

  const handleSend = async (event: React.FormEvent) => {
    event.preventDefault();
    if (numbers.length === 0 || message.trim().length === 0) return;
    setSending(true);
    setSendResult(null);
    try {
      const { data } = await axios.post<ISendResult>(`${API_URL}/send`, { numbers, text: message }, { timeout: 0 });
      setSendResult(data);
      if (data.failed === 0) {
        toast.success(translate('whatsapp.send.success', { count: data.sent }));
      } else {
        toast.warning(translate('whatsapp.send.partial', { sent: data.sent, failed: data.failed }));
      }
    } catch (error) {
      toast.error(errorMessage(error, 'whatsapp.errors.send'));
      if (error?.response?.status === 409) {
        setStatus(current => (current ? { ...current, connected: false } : current));
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <div>
      <h2 id="whatsapp-page-heading" data-cy="WhatsappHeading">
        <Translate contentKey="whatsapp.title">WhatsApp</Translate>
      </h2>
      <p className="text-muted">
        <Translate contentKey="whatsapp.subtitle">Send WhatsApp messages from your own number.</Translate>
      </p>

      <Row>
        <Col lg="5" className="mb-4">
          <Card>
            <Card.Header className="d-flex justify-content-between align-items-center">
              <span>
                <FontAwesomeIcon icon={faQrcode} /> <Translate contentKey="whatsapp.connection.title">Connection</Translate>
              </span>
              <Badge bg={connected ? 'success' : 'warning'} text={connected ? undefined : 'dark'} data-cy="whatsappStatus">
                {connected ? translate('whatsapp.connection.connected') : translate('whatsapp.connection.disconnected')}
              </Badge>
            </Card.Header>
            <Card.Body className="text-center">
              {connecting ? (
                <div className="py-5">
                  <Spinner animation="border" role="status" />
                  <p className="mt-3 mb-0">
                    <Translate contentKey="whatsapp.connection.preparing">Preparing QR Code...</Translate>
                  </p>
                </div>
              ) : connected ? (
                <div className="py-4">
                  <FontAwesomeIcon icon={faCheckCircle} size="3x" className="text-success" />
                  <p className="lead mt-3 mb-1">
                    <Translate contentKey="whatsapp.connection.ready">WhatsApp connected</Translate>
                  </p>
                  <p className="text-muted mb-0">
                    <Translate contentKey="whatsapp.connection.readyHint">You can send messages now.</Translate>
                  </p>
                </div>
              ) : status?.qrCode ? (
                <div>
                  <img src={status.qrCode} alt={translate('whatsapp.connection.qrAlt')} className="img-fluid" style={{ maxWidth: 280 }} />
                  <p className="mt-2 mb-0">
                    <Translate contentKey="whatsapp.connection.qrExpires" interpolate={{ seconds: qrSeconds }}>
                      This QR Code refreshes in {qrSeconds}s.
                    </Translate>
                  </p>
                  {status.pairingCode && (
                    <p className="text-muted small mb-0">
                      <Translate contentKey="whatsapp.connection.pairingCode">Pairing code</Translate>: <code>{status.pairingCode}</code>
                    </p>
                  )}
                </div>
              ) : (
                <p className="text-muted py-5 mb-0">
                  <Translate contentKey="whatsapp.connection.waitingQr">Waiting for the QR Code</Translate>
                </p>
              )}

              {connectionError && (
                <Alert variant="danger" className="mt-3 mb-0 text-start">
                  {connectionError}
                </Alert>
              )}

              {!connected && (
                <ol className="text-start mt-4 mb-0">
                  <li>
                    <Translate contentKey="whatsapp.connection.steps.open">Open WhatsApp on your phone.</Translate>
                  </li>
                  <li>
                    <Translate contentKey="whatsapp.connection.steps.devices">Go to Settings, Linked devices.</Translate>
                  </li>
                  <li>
                    <Translate contentKey="whatsapp.connection.steps.scan">Tap Link a device and scan the QR Code.</Translate>
                  </li>
                </ol>
              )}
            </Card.Body>
            <Card.Footer className="d-flex justify-content-between align-items-center">
              <small className="text-muted">{status?.instanceName}</small>
              <Button
                variant={connected ? 'outline-secondary' : 'primary'}
                size="sm"
                onClick={() => connect(true)}
                disabled={connecting}
                data-cy="whatsappReconnect"
              >
                <FontAwesomeIcon icon={faSync} spin={connecting} />{' '}
                {connected ? translate('whatsapp.connection.switchNumber') : translate('whatsapp.connection.newQr')}
              </Button>
            </Card.Footer>
          </Card>
        </Col>

        <Col lg="7" className="mb-4">
          <Card>
            <Card.Header>
              <FontAwesomeIcon icon={faPaperPlane} /> <Translate contentKey="whatsapp.send.title">Send message</Translate>
            </Card.Header>
            <Card.Body>
              {!connected && (
                <Alert variant="warning">
                  <Translate contentKey="whatsapp.send.connectFirst">Connect your WhatsApp before sending messages.</Translate>
                </Alert>
              )}
              <Form onSubmit={handleSend}>
                <Form.Group className="mb-3" controlId="whatsappNumbers">
                  <Form.Label>
                    <Translate contentKey="whatsapp.send.numbers">Numbers</Translate>
                  </Form.Label>
                  <Form.Control
                    as="textarea"
                    rows={5}
                    value={numbersText}
                    onChange={e => setNumbersText(e.target.value)}
                    placeholder={translate('whatsapp.send.numbersPlaceholder')}
                    disabled={sending}
                    data-cy="whatsappNumbers"
                  />
                  <Form.Text muted>
                    <Translate contentKey="whatsapp.send.numbersHelp" interpolate={{ count: numbers.length }}>
                      One per line or separated by commas. {numbers.length} number(s) detected.
                    </Translate>
                  </Form.Text>
                </Form.Group>

                <Form.Group className="mb-3" controlId="whatsappMessage">
                  <Form.Label>
                    <Translate contentKey="whatsapp.send.message">Message</Translate>
                  </Form.Label>
                  <Form.Control
                    as="textarea"
                    rows={6}
                    value={message}
                    maxLength={MAX_TEXT_LENGTH}
                    onChange={e => setMessage(e.target.value)}
                    placeholder={translate('whatsapp.send.messagePlaceholder')}
                    disabled={sending}
                    data-cy="whatsappMessage"
                  />
                  <Form.Text muted>
                    {message.length}/{MAX_TEXT_LENGTH}
                  </Form.Text>
                </Form.Group>

                <Button
                  type="submit"
                  variant="primary"
                  disabled={!connected || sending || numbers.length === 0 || message.trim().length === 0}
                  data-cy="whatsappSend"
                >
                  {sending ? <Spinner animation="border" size="sm" /> : <FontAwesomeIcon icon={faPaperPlane} />}{' '}
                  {sending ? translate('whatsapp.send.sending') : translate('whatsapp.send.button', { count: numbers.length })}
                </Button>
                {sending && numbers.length > 1 && (
                  <Form.Text muted className="d-block mt-2">
                    <Translate contentKey="whatsapp.send.sendingHint">Messages are sent with a short pause between them.</Translate>
                  </Form.Text>
                )}
              </Form>
            </Card.Body>
          </Card>

          {sendResult && (
            <Card className="mt-4" data-cy="whatsappResults">
              <Card.Header>
                <Translate contentKey="whatsapp.results.title">Result</Translate>{' '}
                <Badge bg="success">{translate('whatsapp.results.sent', { count: sendResult.sent })}</Badge>{' '}
                {sendResult.failed > 0 && <Badge bg="danger">{translate('whatsapp.results.failed', { count: sendResult.failed })}</Badge>}
              </Card.Header>
              <Table responsive striped className="mb-0">
                <thead>
                  <tr>
                    <th>
                      <Translate contentKey="whatsapp.results.number">Number</Translate>
                    </th>
                    <th>
                      <Translate contentKey="whatsapp.results.status">Status</Translate>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {sendResult.results.map(item => (
                    <tr key={item.input}>
                      <td>
                        {item.input}
                        {item.number && item.number !== item.input && <small className="text-muted"> ({item.number})</small>}
                      </td>
                      <td>
                        {item.success ? (
                          <Badge bg="success">{translate('whatsapp.results.ok')}</Badge>
                        ) : (
                          <Badge bg="danger">{item.error}</Badge>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </Card>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default WhatsappPage;
