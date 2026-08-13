/* @oracle/oraclejet-preact: undefined */
import { createContext } from 'preact';
import { useContext } from 'preact/hooks';

const CheckboxSetContext = createContext({});
const useCheckboxSetContext = () => useContext(CheckboxSetContext);

export { CheckboxSetContext as C, useCheckboxSetContext as u };
//# sourceMappingURL=CheckboxSetContext-1be56556.js.map
