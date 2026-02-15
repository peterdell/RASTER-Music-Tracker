#pragma once


template <typename T>
class TypedComboBox : public CComboBox {
public:
    void AddItem(const T value, const CString& text) {
        const auto i = this->AddString(text);
        assert(i != CB_ERRSPACE);
        this->SetItemData(i, (DWORD_PTR)value);
    }

    void SetSelectedItem(const  T value) {
        for (int i = 0; i < this->GetCount(); i++) {
            if (this->GetItemData(i) == (DWORD_PTR)value) {
                this->SetCurSel(i);
                return;
            }
        }
        if (this->GetCount() > 0) {
            this->SetCurSel(0);
        }
    }

    T GetSelectedItem(const T& defaultItem) const {
        auto i = this->GetCurSel();
        if (i != CB_ERR) {
            return (T)this->GetItemData(i);
        }
        return defaultItem;
    }
};



